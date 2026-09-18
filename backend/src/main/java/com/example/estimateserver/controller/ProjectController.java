package com.example.estimateserver.controller;

import com.example.estimateserver.model.Material;
import com.example.estimateserver.model.Project;
import com.example.estimateserver.model.WorkItem;
import com.example.estimateserver.service.SyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/projects")
@CrossOrigin(origins = "*")
public class ProjectController {

    private static final Logger log = LoggerFactory.getLogger(ProjectController.class);

    private final SyncService syncService;

    public ProjectController(SyncService syncService) {
        this.syncService = syncService;
    }

    @GetMapping("/pending/{userId}")
    public ResponseEntity<List<Project>> getPendingShares(@PathVariable String userId) {
        return ResponseEntity.ok(syncService.getPendingProjectsForUser(userId));
    }

    @PostMapping("/{projectId}/accept")
    public ResponseEntity<Void> acceptShare(@PathVariable String projectId,
                                            @RequestHeader("X-User-Id") String userId) {
        syncService.acceptShare(projectId, userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{projectId}/decline")
    public ResponseEntity<Void> declineShare(@PathVariable String projectId,
                                             @RequestHeader("X-User-Id") String userId) {
        syncService.declineShare(projectId, userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{projectId}/share")
    public ResponseEntity<Void> shareProject(@PathVariable String projectId,
                                             @RequestBody Map<String, String> request,
                                             @RequestHeader("X-User-Id") String userId) {
        String phoneNumber = request.get("phoneNumber");
        syncService.shareProjectWithUser(projectId, phoneNumber, userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/shared/{userId}")
    public ResponseEntity<List<Project>> getSharedProjects(@PathVariable String userId) {
        return ResponseEntity.ok(syncService.getProjectsSharedWithUser(userId));
    }

    @GetMapping
    public ResponseEntity<List<Project>> getAllProjects(@RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(syncService.getProjectsForUser(userId));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<Project>> getProjectsForUser(@PathVariable String userId) {
        return ResponseEntity.ok(syncService.getProjectsForUser(userId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Project> getProject(@PathVariable String id,
                                              @RequestHeader("X-User-Id") String userId) {
        Optional<Project> project = syncService.getProject(id);
        if (project.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        if (!syncService.hasAccessToProject(id, userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(project.get());
    }

    @PostMapping
    public ResponseEntity<Project> createOrUpdateProject(@RequestBody Project project,
                                                         @RequestHeader("X-User-Id") String userId) {
        try {
            project.setUserId(null);

            Project result = syncService.saveProject(project, userId);
            log.info("Project saved: {} by user {}", result.getId(), userId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error saving project {}: {}", project.getId(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<Project> updateProject(@PathVariable String id,
                                                 @RequestBody Project project,
                                                 @RequestHeader("X-User-Id") String userId) {
        project.setId(id);
        Project updated = syncService.updateProject(project, userId);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProject(@PathVariable String id,
                                              @RequestHeader("X-User-Id") String userId) {
        syncService.deleteProject(id, userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{projectId}/materials")
    public ResponseEntity<List<Material>> getMaterials(@PathVariable String projectId,
                                                       @RequestHeader("X-User-Id") String userId) {
        if (!syncService.hasAccessToProject(projectId, userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(syncService.getMaterials(projectId));
    }

    @GetMapping("/{projectId}/work-items")
    public ResponseEntity<List<WorkItem>> getWorkItems(@PathVariable String projectId,
                                                       @RequestHeader("X-User-Id") String userId) {
        if (!syncService.hasAccessToProject(projectId, userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(syncService.getWorkItems(projectId));
    }
}