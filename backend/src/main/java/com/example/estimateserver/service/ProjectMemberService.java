package com.example.estimateserver.service;

import com.example.estimateserver.dto.SyncMessage;
import com.example.estimateserver.model.ProjectMember;
import com.example.estimateserver.model.ProjectRole;
import com.example.estimateserver.model.User;
import com.example.estimateserver.repository.ProjectMemberRepository;
import com.example.estimateserver.repository.ProjectRepository;
import com.example.estimateserver.repository.UserRepository;
import com.example.estimateserver.utils.PhoneUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ProjectMemberService {

    private static final Logger log = LoggerFactory.getLogger(ProjectMemberService.class);

    private final ProjectMemberRepository memberRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final ProjectAccessService accessService;
    private final WebSocketService webSocketService;

    public ProjectMemberService(ProjectMemberRepository memberRepository,
                                ProjectRepository projectRepository,
                                UserRepository userRepository,
                                ProjectAccessService accessService,
                                WebSocketService webSocketService) {
        this.memberRepository = memberRepository;
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.accessService = accessService;
        this.webSocketService = webSocketService;
    }

    @Transactional(readOnly = true)
    public Map<String, List<ProjectMember>> getMembersByProjectIds(List<String> projectIds) {
        if (projectIds == null || projectIds.isEmpty()) {
            return Map.of();
        }
        return memberRepository.findByProjectIdIn(projectIds).stream()
                .collect(Collectors.groupingBy(ProjectMember::getProjectId));
    }

    @Transactional(readOnly = true)
    public List<ProjectMember> getMembers(String projectId, String actorId) {
        accessService.require(projectId, actorId, ProjectAccessService.Permission.VIEW);
        return memberRepository.findByProjectId(projectId);
    }

    @Transactional(readOnly = true)
    public Optional<ProjectRole> getMyRole(String projectId, String userId) {
        return accessService.getRole(projectId, userId);
    }

    @Transactional
    public ProjectMember setCustomer(String projectId, String targetUserPhone,
                                     String actorId, boolean bootstrap) {
        requireProjectExists(projectId);

        if (!bootstrap) {
            accessService.require(projectId, actorId,
                    ProjectAccessService.Permission.MANAGE_MEMBERS);
        }

        User target = resolveUserByPhone(targetUserPhone);

        replaceSingleRole(projectId, target, ProjectRole.CUSTOMER, actorId);
        ProjectMember saved = memberRepository
                .findByProjectIdAndUserId(projectId, target.getId())
                .orElseThrow(() -> new IllegalStateException("CUSTOMER не назначен"));

        notifyMembers(projectId, "MEMBER_ADDED", saved, actorId);
        log.info("CUSTOMER set: project={}, user={}", projectId, target.getId());
        return saved;
    }

    @Transactional
    public ProjectMember setEstimator(String projectId, String targetUserPhone, String actorId) {
        requireProjectExists(projectId);
        accessService.require(projectId, actorId,
                ProjectAccessService.Permission.MANAGE_MEMBERS);

        User target = resolveUserByPhone(targetUserPhone);

        if (isSameAsActor(target.getId(), actorId)) {
            throw new IllegalArgumentException("Нельзя назначить себя сметчиком");
        }

        replaceSingleRole(projectId, target, ProjectRole.ESTIMATOR, actorId);
        ProjectMember saved = memberRepository
                .findByProjectIdAndUserId(projectId, target.getId())
                .orElseThrow(() -> new IllegalStateException("ESTIMATOR не назначен"));

        notifyMembers(projectId, "MEMBER_ADDED", saved, actorId);
        log.info("ESTIMATOR set: project={}, user={}", projectId, target.getId());
        return saved;
    }

    @Transactional
    public AddBuildersResult addBuilders(String projectId, List<String> phones,
                                         String actorId) {
        requireProjectExists(projectId);
        accessService.require(projectId, actorId,
                ProjectAccessService.Permission.MANAGE_BUILDERS);

        AddBuildersResult result = new AddBuildersResult();

        if (phones == null || phones.isEmpty()) {
            return result;
        }

        Set<String> normalized = new LinkedHashSet<>();
        for (String raw : phones) {
            String n = PhoneUtils.normalize(raw);
            if (n != null) normalized.add(n);
        }
        normalized.removeIf(n -> userRepository.findByPhoneNumber(n)
                .map(u -> u.getId().equals(actorId))
                .orElse(false));

        for (String phone : normalized) {
            Optional<User> userOpt = userRepository.findByPhoneNumber(phone);
            if (userOpt.isEmpty()) {
                log.warn("addBuilders: user not found for phone={}", PhoneUtils.mask(phone));
                result.notFound.add(phone);
                continue;
            }
            User user = userOpt.get();

            if (memberRepository.existsByProjectIdAndUserId(projectId, user.getId())) {
                log.debug("addBuilders: user {} already a member", user.getId());
                result.alreadyMembers.add(phone);
                continue;
            }

            ProjectMember member = new ProjectMember(
                    projectId, user.getId(), ProjectRole.BUILDER, actorId);
            ProjectMember saved = memberRepository.save(member);
            result.added.add(saved);
            notifyMembers(projectId, "MEMBER_ADDED", saved, actorId);
        }

        log.info("addBuilders: project={}, actor={}, added={}, notFound={}, already={}",
                projectId, actorId, result.added.size(),
                result.notFound.size(), result.alreadyMembers.size());

        if (result.added.isEmpty() && !normalized.isEmpty()) {
            if (!result.notFound.isEmpty()) {
                throw new MemberNotFoundException(
                        "Не удалось добавить ни одного участника: пользователи не найдены в приложении");
            }
            if (!result.alreadyMembers.isEmpty()) {
                throw new MemberAlreadyExistsException(
                        "Все указанные участники уже добавлены в проект");
            }
        }

        return result;
    }

    @Transactional
    public void removeMember(String projectId, String targetUserId, String actorId) {
        requireProjectExists(projectId);

        ProjectMember member = memberRepository
                .findByProjectIdAndUserId(projectId, targetUserId)
                .orElseThrow(() -> new MemberNotFoundException(
                        "Участник не найден в проекте"));

        boolean isCustomer = accessService.isCustomer(projectId, actorId);
        boolean isEstimator = accessService.isEstimator(projectId, actorId);

        if (!isCustomer && !isEstimator) {
            throw new AccessDeniedException(
                    "Недостаточно прав для удаления участника");
        }

        if (isEstimator && !isCustomer && member.getRole() != ProjectRole.BUILDER) {
            throw new AccessDeniedException(
                    "Сметчик может удалять только строителей");
        }

        if (member.getRole() == ProjectRole.CUSTOMER) {
            throw new MemberLimitException(
                    "Нельзя удалить заказчика. Назначьте другого заказчика.");
        }

        memberRepository.delete(member);

        notifyMembers(projectId, "MEMBER_REMOVED", targetUserId, actorId, targetUserId);

        log.info("Member removed: project={}, user={}, role={}, actor={}",
                projectId, targetUserId, member.getRole(), actorId);
    }

    @Transactional
    public ProjectMember changeRole(String projectId, String targetUserId,
                                    ProjectRole newRole, String actorId) {
        requireProjectExists(projectId);
        accessService.require(projectId, actorId,
                ProjectAccessService.Permission.MANAGE_MEMBERS);

        if (newRole == ProjectRole.CUSTOMER) {
            throw new IllegalArgumentException(
                    "Для смены заказчика используйте setCustomer");
        }

        ProjectMember member = memberRepository
                .findByProjectIdAndUserId(projectId, targetUserId)
                .orElseThrow(() -> new MemberNotFoundException(
                        "Участник не найден в проекте"));

        if (member.getRole() == ProjectRole.CUSTOMER) {
            throw new MemberLimitException(
                    "Нельзя изменить роль заказчика. Назначьте другого заказчика.");
        }

        if (member.getRole() == newRole) {
            return member;
        }

        if (newRole == ProjectRole.ESTIMATOR) {
            memberRepository.findFirstByProjectIdAndRole(projectId, ProjectRole.ESTIMATOR)
                    .filter(other -> !other.getUserId().equals(targetUserId))
                    .ifPresent(other -> {
                        other.setRole(ProjectRole.BUILDER);
                        memberRepository.save(other);
                        notifyMembers(projectId, "MEMBER_UPDATED", other, actorId);
                    });
        }

        member.setRole(newRole);
        ProjectMember saved = memberRepository.save(member);
        notifyMembers(projectId, "MEMBER_UPDATED", saved, actorId);
        log.info("Member role changed: project={}, user={}, newRole={}",
                projectId, targetUserId, newRole);
        return saved;
    }

    @Transactional
    public ProjectMember bootstrapCustomer(String projectId, String customerUserId,
                                           String creatorId) {
        Optional<ProjectMember> existing = memberRepository
                .findByProjectIdAndUserId(projectId, customerUserId);
        if (existing.isPresent()) {
            return existing.get();
        }

        Optional<ProjectMember> currentCustomer =
                memberRepository.findFirstByProjectIdAndRole(projectId, ProjectRole.CUSTOMER);
        if (currentCustomer.isPresent()) {
            return currentCustomer.get();
        }

        ProjectMember member = new ProjectMember(
                projectId, customerUserId, ProjectRole.CUSTOMER, creatorId);
        ProjectMember saved = memberRepository.save(member);
        log.info("bootstrapCustomer: project={}, user={}", projectId, customerUserId);
        return saved;
    }

    @Transactional
    public long deleteAllForProject(String projectId) {
        return memberRepository.deleteByProjectId(projectId);
    }

    @Transactional(readOnly = true)
    public List<String> getMemberUserIds(String projectId) {
        return memberRepository.findUserIdsByProjectId(projectId);
    }

    @Transactional(readOnly = true)
    public Optional<String> getCustomerUserId(String projectId) {
        return memberRepository.findFirstByProjectIdAndRole(projectId, ProjectRole.CUSTOMER)
                .map(ProjectMember::getUserId);
    }

    @Transactional(readOnly = true)
    public Optional<String> getEstimatorUserId(String projectId) {
        return memberRepository.findFirstByProjectIdAndRole(projectId, ProjectRole.ESTIMATOR)
                .map(ProjectMember::getUserId);
    }

    @Transactional(readOnly = true)
    public List<String> getBuilderUserIds(String projectId) {
        return memberRepository.findByProjectIdAndRole(projectId, ProjectRole.BUILDER)
                .stream()
                .map(ProjectMember::getUserId)
                .toList();
    }

    private void requireProjectExists(String projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw new SyncService.ProjectNotFoundException("Проект не найден");
        }
    }

    private User resolveUserByPhone(String rawPhone) {
        String phone = PhoneUtils.normalize(rawPhone);
        if (phone == null) {
            throw new IllegalArgumentException("Некорректный номер телефона");
        }
        return userRepository.findByPhoneNumber(phone)
                .orElseThrow(() -> new MemberNotFoundException(
                        "Пользователь с таким номером не найден в приложении"));
    }

    private boolean isSameAsActor(String targetUserId, String actorId) {
        return targetUserId != null && targetUserId.equals(actorId);
    }

    private void replaceSingleRole(String projectId, User target, ProjectRole role,
                                   String actorId) {
        memberRepository.findFirstByProjectIdAndRole(projectId, role)
                .filter(m -> !m.getUserId().equals(target.getId()))
                .ifPresent(old -> {
                    memberRepository.delete(old);
                    notifyMembers(projectId, "MEMBER_REMOVED", old.getUserId(), actorId);
                    log.info("Replaced {} in project {}: old user {} removed",
                            role, projectId, old.getUserId());
                });

        Optional<ProjectMember> existing =
                memberRepository.findByProjectIdAndUserId(projectId, target.getId());

        if (existing.isPresent()) {
            ProjectMember m = existing.get();
            if (m.getRole() != role) {
                m.setRole(role);
                memberRepository.save(m);
            }
            return;
        }

        ProjectMember member = new ProjectMember(projectId, target.getId(), role, actorId);
        memberRepository.save(member);
    }

    private void notifyMembers(String projectId, String type, Object payload, String actorId) {
        notifyMembers(projectId, type, payload, actorId, null);
    }

    private void notifyMembers(String projectId, String type, Object payload,
                               String actorId, String excludedUserId) {
        List<String> recipients = memberRepository.findUserIdsByProjectId(projectId);

        String entityId;
        if (payload instanceof ProjectMember pm) {
            entityId = pm.getUserId();
        } else if (payload instanceof String s) {
            entityId = s;
        } else {
            entityId = null;
        }

        Map<String, Object> data = new HashMap<>();
        if (payload instanceof ProjectMember pm) {
            data.put("projectId", pm.getProjectId());
            data.put("userId", pm.getUserId());
            data.put("role", pm.getRole().name());
            data.put("addedBy", pm.getAddedBy());
        } else if (payload instanceof String s) {
            data.put("projectId", projectId);
            data.put("userId", s);
        }

        SyncMessage message = new SyncMessage(
                type,
                "PROJECT_MEMBER",
                entityId,
                data,
                actorId,
                new Date(),
                null
        );

        for (String userId : recipients) {
            if (userId.equals(actorId)) continue;
            if (excludedUserId != null && userId.equals(excludedUserId)) continue;

            webSocketService.sendToUserOrQueue(userId, message);
        }
    }

    public static class AddBuildersResult {
        public List<ProjectMember> added = new ArrayList<>();
        public List<String> notFound = new ArrayList<>();
        public List<String> alreadyMembers = new ArrayList<>();

        public int getAddedCount() { return added.size(); }
        public int getNotFoundCount() { return notFound.size(); }
        public int getAlreadyCount() { return alreadyMembers.size(); }
    }
}