package com.example.estimateserver.service;

import com.example.estimateserver.dto.ProjectSnapshot;
import com.example.estimateserver.dto.SyncMessage;
import com.example.estimateserver.model.ChangeKind;
import com.example.estimateserver.model.ChangeStatus;
import com.example.estimateserver.model.Project;
import com.example.estimateserver.model.ProjectChangeRequest;
import com.example.estimateserver.model.ProjectMember;
import com.example.estimateserver.model.ProjectRole;
import com.example.estimateserver.model.ProjectState;
import com.example.estimateserver.repository.ProjectChangeRequestRepository;
import com.example.estimateserver.repository.ProjectMemberRepository;
import com.example.estimateserver.repository.ProjectRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ChangeRequestService {

    private static final Logger log = LoggerFactory.getLogger(ChangeRequestService.class);

    private final ProjectChangeRequestRepository changeRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository memberRepository;
    private final ProjectAccessService accessService;
    private final WebSocketService webSocketService;
    private final ObjectMapper objectMapper;
    private final SnapshotApplier snapshotApplier;

    public ChangeRequestService(ProjectChangeRequestRepository changeRepository,
                                ProjectRepository projectRepository,
                                ProjectMemberRepository memberRepository,
                                ProjectAccessService accessService,
                                WebSocketService webSocketService,
                                ObjectMapper objectMapper,
                                SnapshotApplier snapshotApplier) {
        this.changeRepository = changeRepository;
        this.projectRepository = projectRepository;
        this.memberRepository = memberRepository;
        this.accessService = accessService;
        this.webSocketService = webSocketService;
        this.objectMapper = objectMapper;
        this.snapshotApplier = snapshotApplier;
    }

    @Transactional(readOnly = true)
    public List<ProjectChangeRequest> getAllForProject(String projectId, String actorId) {
        accessService.require(projectId, actorId, ProjectAccessService.Permission.VIEW);
        return changeRepository.findByProjectIdOrderByCreatedAtDesc(projectId);
    }

    @Transactional(readOnly = true)
    public List<ProjectChangeRequest> getPendingForProject(String projectId, String actorId) {
        accessService.require(projectId, actorId, ProjectAccessService.Permission.VIEW);
        return changeRepository.findByProjectIdAndStatusOrderByCreatedAtDesc(
                projectId, ChangeStatus.PENDING);
    }

    @Transactional(readOnly = true)
    public List<ProjectChangeRequest> getInboxForUser(String userId) {
        List<ProjectMember> memberships = memberRepository.findByUserId(userId);
        if (memberships.isEmpty()) return List.of();

        Set<String> fullAccessProjectIds = memberships.stream()
                .filter(m -> m.getRole() == ProjectRole.CUSTOMER
                        || m.getRole() == ProjectRole.ESTIMATOR)
                .map(ProjectMember::getProjectId)
                .collect(Collectors.toSet());

        Set<String> builderProjectIds = memberships.stream()
                .filter(m -> m.getRole() == ProjectRole.BUILDER)
                .map(ProjectMember::getProjectId)
                .collect(Collectors.toSet());

        List<ProjectChangeRequest> result = new ArrayList<>();

        if (!fullAccessProjectIds.isEmpty()) {
            result.addAll(changeRepository.findAllForProjects(fullAccessProjectIds));
        }

        if (!builderProjectIds.isEmpty()) {
            List<ProjectChangeRequest> builderVisible =
                    changeRepository.findAllForProjects(builderProjectIds).stream()
                            .filter(r -> r.getStatus() == ChangeStatus.APPROVED
                                    && r.getKind() == ChangeKind.ESTIMATE_EDIT)
                            .toList();
            result.addAll(builderVisible);
        }

        result.sort(Comparator.comparing(ProjectChangeRequest::getCreatedAt).reversed());
        return result;
    }

    @Transactional
    public ProjectChangeRequest submitEstimateEdit(String projectId,
                                                   ProjectSnapshot snapshot,
                                                   String authorId) {
        accessService.require(projectId, authorId,
                ProjectAccessService.Permission.EDIT_ESTIMATE);

        if (changeRepository.existsByProjectIdAndKindAndStatus(
                projectId, ChangeKind.ESTIMATE_EDIT, ChangeStatus.PENDING)) {
            throw new IllegalStateException(
                    "Уже есть смета, ожидающая решения заказчика");
        }

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new SyncService.ProjectNotFoundException("Проект не найден"));

        ProjectChangeRequest change = new ProjectChangeRequest(
                projectId, authorId, ChangeKind.ESTIMATE_EDIT);
        change.setStatus(ChangeStatus.PENDING);
        change.setComment(snapshot.getComment());
        change.setPayloadJson(serialize(snapshot));
        change.setCreatedAt(new Date());

        ProjectChangeRequest saved = changeRepository.save(change);

        project.setState(ProjectState.PENDING_APPROVAL);
        project.setHasPendingChanges(true);
        project.setUpdatedAt(new Date());
        projectRepository.save(project);

        notifyCustomerOnly(projectId, "CHANGE_SUBMITTED", saved);

        log.info("Estimate change submitted: project={}, author={}, changeId={}",
                projectId, authorId, saved.getId());
        return saved;
    }

    @Transactional
    public ProjectChangeRequest submitComment(String projectId,
                                              String text,
                                              String entityType,
                                              String entityId,
                                              String authorId) {
        accessService.require(projectId, authorId,
                ProjectAccessService.Permission.COMMENT);

        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Комментарий не может быть пустым");
        }

        ProjectChangeRequest change = new ProjectChangeRequest(
                projectId, authorId, ChangeKind.COMMENT);
        change.setStatus(ChangeStatus.APPROVED);
        change.setComment(text);
        change.setCreatedAt(new Date());

        Map<String, String> payload = new HashMap<>();
        payload.put("entityType", entityType);
        payload.put("entityId", entityId);
        change.setPayloadJson(serializeMap(payload));

        ProjectChangeRequest saved = changeRepository.save(change);

        notifyAllMembers(projectId, "COMMENT_ADDED", saved, authorId);

        log.info("Comment added: project={}, author={}, changeId={}",
                projectId, authorId, saved.getId());
        return saved;
    }

    @Transactional
    public ProjectChangeRequest approveChange(String changeId,
                                              String actorId,
                                              String comment) {
        ProjectChangeRequest change = changeRepository.findById(changeId)
                .orElseThrow(() -> new ChangeRequestNotFoundException(
                        "Запрос не найден"));

        if (change.getStatus() != ChangeStatus.PENDING) {
            throw new IllegalStateException(
                    "Запрос уже обработан: " + change.getStatus());
        }

        if (change.getKind() != ChangeKind.ESTIMATE_EDIT) {
            throw new IllegalStateException(
                    "Утверждать можно только изменения сметы");
        }

        accessService.require(change.getProjectId(), actorId,
                ProjectAccessService.Permission.APPROVE);

        Project project = projectRepository.findById(change.getProjectId())
                .orElseThrow(() -> new SyncService.ProjectNotFoundException("Проект не найден"));

        ProjectSnapshot snapshot = deserialize(change.getPayloadJson());
        snapshotApplier.apply(project, snapshot, actorId);

        project.setState(ProjectState.APPROVED);
        project.setHasPendingChanges(false);
        projectRepository.save(project);

        change.setStatus(ChangeStatus.APPROVED);
        change.setReviewerId(actorId);
        change.setReviewedAt(new Date());
        if (comment != null && !comment.isBlank()) {
            change.setReviewComment(comment);
        }

        ProjectChangeRequest saved = changeRepository.save(change);

        notifyAllMembers(change.getProjectId(), "CHANGE_APPROVED", saved, actorId);

        log.info("Change approved: changeId={}, project={}, reviewer={}",
                changeId, change.getProjectId(), actorId);
        return saved;
    }

    @Transactional
    public ProjectChangeRequest rejectChange(String changeId,
                                             String comment,
                                             String actorId) {
        ProjectChangeRequest change = changeRepository.findById(changeId)
                .orElseThrow(() -> new ChangeRequestNotFoundException(
                        "Запрос не найден"));

        if (change.getStatus() != ChangeStatus.PENDING) {
            throw new IllegalStateException(
                    "Запрос уже обработан: " + change.getStatus());
        }

        if (change.getKind() != ChangeKind.ESTIMATE_EDIT) {
            throw new IllegalStateException(
                    "Отклонять можно только изменения сметы");
        }

        accessService.require(change.getProjectId(), actorId,
                ProjectAccessService.Permission.APPROVE);

        if (comment == null || comment.isBlank()) {
            throw new IllegalArgumentException(
                    "При отклонении нужно указать причину");
        }

        change.setStatus(ChangeStatus.REJECTED);
        change.setReviewerId(actorId);
        change.setReviewComment(comment);
        change.setReviewedAt(new Date());

        ProjectChangeRequest saved = changeRepository.save(change);

        Project project = projectRepository.findById(change.getProjectId())
                .orElseThrow(() -> new SyncService.ProjectNotFoundException("Проект не найден"));
        project.setState(ProjectState.REJECTED);
        project.setHasPendingChanges(false);
        project.setUpdatedAt(new Date());
        project.setLastModifiedBy(actorId);
        projectRepository.save(project);

        notifyUser(change.getAuthorId(), change.getProjectId(),
                "CHANGE_REJECTED", saved, actorId);
        notifyAllMembersExcept(change.getProjectId(), change.getAuthorId(),
                "CHANGE_REJECTED", saved, actorId);

        log.info("Change rejected: changeId={}, project={}, reviewer={}",
                changeId, change.getProjectId(), actorId);
        return saved;
    }

    @Transactional
    public void deleteRejectedDraft(String changeId, String actorId) {
        ProjectChangeRequest change = changeRepository.findById(changeId)
                .orElseThrow(() -> new ChangeRequestNotFoundException(
                        "Запрос не найден"));

        if (!change.getAuthorId().equals(actorId)) {
            throw new AccessDeniedException(
                    "Удалить черновик может только его автор");
        }

        if (change.getStatus() != ChangeStatus.REJECTED) {
            throw new IllegalStateException(
                    "Удалить можно только отклонённый черновик");
        }

        changeRepository.delete(change);
        log.info("Rejected draft deleted: changeId={}, author={}",
                changeId, actorId);
    }

    @Transactional(readOnly = true)
    public Optional<ProjectChangeRequest> getPendingEstimateChange(String projectId, String actorId) {
        accessService.require(projectId, actorId, ProjectAccessService.Permission.VIEW);

        return changeRepository
                .findFirstByProjectIdAndKindAndStatusOrderByCreatedAtDesc(
                        projectId, ChangeKind.ESTIMATE_EDIT, ChangeStatus.PENDING);
    }

    @Transactional
    public long deleteAllForProject(String projectId) {
        return changeRepository.deleteByProjectId(projectId);
    }

    private void notifyCustomerOnly(String projectId, String type,
                                    ProjectChangeRequest change) {
        memberRepository.findFirstByProjectIdAndRole(projectId, ProjectRole.CUSTOMER)
                .ifPresent(customer ->
                        notifyUser(customer.getUserId(), projectId, type, change, change.getAuthorId()));
    }

    private void notifyAllMembers(String projectId, String type,
                                  ProjectChangeRequest change, String actorId) {
        for (String userId : memberRepository.findUserIdsByProjectId(projectId)) {
            notifyUser(userId, projectId, type, change, actorId);
        }
    }

    private void notifyAllMembersExcept(String projectId, String excludedUserId,
                                        String type, ProjectChangeRequest change,
                                        String actorId) {
        for (String userId : memberRepository.findUserIdsByProjectId(projectId)) {
            if (userId.equals(excludedUserId)) continue;
            notifyUser(userId, projectId, type, change, actorId);
        }
    }

    private void notifyUser(String userId, String projectId, String type,
                            ProjectChangeRequest change, String actorId) {
        if (userId == null || userId.equals(actorId)) return;

        Map<String, Object> data = new HashMap<>();
        data.put("projectId", projectId);
        data.put("changeId", change.getId());
        data.put("kind", change.getKind().name());
        data.put("status", change.getStatus().name());
        data.put("authorId", change.getAuthorId());
        data.put("comment", change.getComment());
        data.put("reviewComment", change.getReviewComment());
        data.put("createdAt", change.getCreatedAt());

        SyncMessage message = new SyncMessage(
                type,
                "CHANGE_REQUEST",
                change.getId(),
                data,
                actorId,
                new Date(),
                change.getVersion()
        );

        webSocketService.sendToUserOrQueue(userId, message);
    }

    private String serialize(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Не удалось сериализовать снапшот", e);
        }
    }

    private String serializeMap(Map<String, String> map) {
        return serialize(map);
    }

    private ProjectSnapshot deserialize(String json) {
        try {
            return objectMapper.readValue(json, ProjectSnapshot.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Не удалось разобрать снапшот", e);
        }
    }

    public static class ChangeRequestNotFoundException extends RuntimeException {
        public ChangeRequestNotFoundException(String message) {
            super(message);
        }
    }
}