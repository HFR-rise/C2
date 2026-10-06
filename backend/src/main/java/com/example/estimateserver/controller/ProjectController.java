package com.example.estimateserver.controller;

import com.example.estimateserver.dto.ProjectDto;
import com.example.estimateserver.dto.ProjectMemberDto;
import com.example.estimateserver.dto.ProjectSnapshot;
import com.example.estimateserver.model.Material;
import com.example.estimateserver.model.Project;
import com.example.estimateserver.model.ProjectMember;
import com.example.estimateserver.model.ProjectRole;
import com.example.estimateserver.model.User;
import com.example.estimateserver.model.WorkItem;
import com.example.estimateserver.repository.MaterialRepository;
import com.example.estimateserver.repository.UserRepository;
import com.example.estimateserver.repository.WorkItemRepository;
import com.example.estimateserver.service.AccessDeniedException;
import com.example.estimateserver.service.ProjectAccessService;
import com.example.estimateserver.service.ProjectMemberService;
import com.example.estimateserver.service.SyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private static final Logger log = LoggerFactory.getLogger(ProjectController.class);

    private static final String HEADER_USER_ID = "X-User-Id";

    private final SyncService syncService;
    private final ProjectAccessService accessService;
    private final ProjectMemberService projectMemberService;
    private final UserRepository userRepository;
    private final MaterialRepository materialRepository;
    private final WorkItemRepository workItemRepository;

    public ProjectController(SyncService syncService,
                             ProjectAccessService accessService,
                             ProjectMemberService projectMemberService,
                             UserRepository userRepository,
                             MaterialRepository materialRepository,
                             WorkItemRepository workItemRepository) {
        this.syncService = syncService;
        this.accessService = accessService;
        this.projectMemberService = projectMemberService;
        this.userRepository = userRepository;
        this.materialRepository = materialRepository;
        this.workItemRepository = workItemRepository;
    }

    @GetMapping
    public ResponseEntity<List<ProjectDto>> getAllProjects(
            @RequestHeader(HEADER_USER_ID) String userId) {
        List<Project> projects = syncService.getProjectsForUser(userId);
        return ResponseEntity.ok(buildProjectDtos(projects, userId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getProject(@PathVariable String id,
                                        @RequestHeader(HEADER_USER_ID) String userId) {
        Optional<Project> projectOpt = syncService.getProject(id);
        if (projectOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        if (!syncService.hasAccessToProject(id, userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Access denied"));
        }

        List<ProjectDto> dtos = buildProjectDtos(List.of(projectOpt.get()), userId);
        return ResponseEntity.ok(dtos.get(0));
    }

    @PostMapping("/{id}/sync")
    public ResponseEntity<?> syncProject(
            @PathVariable String id,
            @RequestBody ProjectSnapshot snapshot,
            @RequestHeader(HEADER_USER_ID) String userId) {
        try {
            Project updated = syncService.applySnapshotAndSync(id, snapshot, userId);

            List<ProjectDto> dtos = buildProjectDtos(List.of(updated), userId);
            return ResponseEntity.ok(dtos.get(0));

        } catch (AccessDeniedException e) {
            log.warn("Access denied syncing project {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", e.getMessage()));

        } catch (SyncService.ProjectNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));

        } catch (IllegalArgumentException e) {
            log.warn("Bad request syncing project {}: {}", id, e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            log.error("Error syncing project {}: {}", id, e.getMessage(), e);
            String message = e.getMessage() != null ? e.getMessage() : "Internal server error";
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", message));
        }
    }

    @PostMapping
    public ResponseEntity<?> createOrUpdateProject(
            @RequestBody Project project,
            @RequestHeader(HEADER_USER_ID) String userId) {
        try {
            Project result = syncService.saveProject(project, userId);
            log.info("Project saved: {} by user {}", result.getId(), userId);

            List<ProjectDto> dtos = buildProjectDtos(List.of(result), userId);
            return ResponseEntity.ok(dtos.get(0));

        } catch (DataIntegrityViolationException e) {
            log.warn("Duplicate project id {} — falling back to update: {}",
                    safeId(project), e.getMessage());

            try {
                Project existing = syncService.updateProject(project, userId);
                List<ProjectDto> dtos = buildProjectDtos(List.of(existing), userId);
                return ResponseEntity.ok(dtos.get(0));
            } catch (Exception inner) {
                log.error("Fallback update failed for {}: {}",
                        safeId(project), inner.getMessage(), inner);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("error", inner.getMessage() != null
                                ? inner.getMessage() : "Internal server error"));
            }

        } catch (AccessDeniedException e) {
            log.warn("Access denied saving project {}: {}", safeId(project), e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", e.getMessage()));

        } catch (IllegalArgumentException e) {
            log.warn("Bad request saving project {}: {}", safeId(project), e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            log.error("Error saving project {}: {}", safeId(project), e.getMessage(), e);
            String message = e.getMessage() != null ? e.getMessage() : "Internal server error";
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", message));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateProject(@PathVariable String id,
                                           @RequestBody Project project,
                                           @RequestHeader(HEADER_USER_ID) String userId) {
        try {
            project.setId(id);
            Project updated = syncService.updateProject(project, userId);

            List<ProjectDto> dtos = buildProjectDtos(List.of(updated), userId);
            return ResponseEntity.ok(dtos.get(0));

        } catch (AccessDeniedException e) {
            log.warn("Access denied updating project {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", e.getMessage()));

        } catch (IllegalArgumentException e) {
            log.warn("Bad request updating project {}: {}", id, e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            log.error("Error updating project {}: {}", id, e.getMessage(), e);
            String message = e.getMessage() != null ? e.getMessage() : "Internal server error";
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", message));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteProject(@PathVariable String id,
                                           @RequestHeader(HEADER_USER_ID) String userId) {
        try {
            syncService.deleteProject(id, userId);
            return ResponseEntity.ok().build();

        } catch (AccessDeniedException e) {
            log.warn("Access denied deleting project {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            log.error("Error deleting project {}: {}", id, e.getMessage(), e);
            String message = e.getMessage() != null ? e.getMessage() : "Internal server error";
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", message));
        }
    }

    @GetMapping("/{projectId}/materials")
    public ResponseEntity<?> getMaterials(
            @PathVariable String projectId,
            @RequestHeader(HEADER_USER_ID) String userId) {
        if (!syncService.hasAccessToProject(projectId, userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Access denied"));
        }
        return ResponseEntity.ok(syncService.getMaterials(projectId));
    }

    @GetMapping("/{projectId}/work-items")
    public ResponseEntity<?> getWorkItems(
            @PathVariable String projectId,
            @RequestHeader(HEADER_USER_ID) String userId) {
        if (!syncService.hasAccessToProject(projectId, userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Access denied"));
        }
        return ResponseEntity.ok(syncService.getWorkItems(projectId));
    }

    private List<ProjectDto> buildProjectDtos(List<Project> projects, String requesterId) {
        if (projects == null || projects.isEmpty()) {
            return List.of();
        }

        List<String> projectIds = projects.stream()
                .map(Project::getId)
                .toList();

        Map<String, List<ProjectMember>> membersByProject =
                projectMemberService.getMembersByProjectIds(projectIds);

        Map<String, List<Material>> materialsByProject =
                materialRepository.findByProjectIdIn(projectIds).stream()
                        .collect(Collectors.groupingBy(Material::getProjectId));

        Map<String, List<WorkItem>> worksByProject =
                workItemRepository.findByProjectIdIn(projectIds).stream()
                        .collect(Collectors.groupingBy(WorkItem::getProjectId));

        Set<String> userIds = membersByProject.values().stream()
                .flatMap(List::stream)
                .map(ProjectMember::getUserId)
                .collect(Collectors.toSet());

        Map<String, User> usersById = userIds.isEmpty()
                ? Map.of()
                : userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        return projects.stream()
                .map(p -> {
                    ProjectRole myRole = accessService
                            .getRole(p.getId(), requesterId)
                            .orElse(null);

                    ProjectDto dto = ProjectDto.from(p, myRole);

                    List<ProjectMember> members =
                            membersByProject.getOrDefault(p.getId(), List.of());

                    List<ProjectMemberDto> memberDtos = members.stream()
                            .map(m -> {
                                User u = usersById.get(m.getUserId());
                                String phone = u != null ? u.getPhoneNumber() : null;
                                String name = u != null ? u.getName() : null;
                                return ProjectMemberDto.from(m, phone, name);
                            })
                            .toList();

                    dto.setMembers(memberDtos);

                    dto.setMaterials(materialsByProject.getOrDefault(p.getId(), List.of()));
                    dto.setWorkItems(worksByProject.getOrDefault(p.getId(), List.of()));

                    return dto;
                })
                .toList();
    }

    private static String safeId(Project project) {
        if (project == null) return "<null>";
        String id = project.getId();
        return (id != null && !id.isBlank()) ? id : "<no-id>";
    }
}