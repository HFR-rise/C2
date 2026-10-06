package com.example.estimateserver.controller;

import com.example.estimateserver.model.WorkItem;
import com.example.estimateserver.service.ProjectAccessService;
import com.example.estimateserver.service.SyncService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/work-items")
@CrossOrigin(origins = "*")
public class WorkItemController {

    private final SyncService syncService;
    private final ProjectAccessService accessService;

    public WorkItemController(SyncService syncService,
                              ProjectAccessService accessService) {
        this.syncService = syncService;
        this.accessService = accessService;
    }

    @GetMapping("/project/{projectId}")
    public ResponseEntity<List<WorkItem>> getWorkItemsByProject(
            @PathVariable String projectId,
            @RequestHeader("X-User-Id") String userId) {
        if (!syncService.hasAccessToProject(projectId, userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(syncService.getWorkItems(projectId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<WorkItem> getWorkItemById(
            @PathVariable String id,
            @RequestHeader("X-User-Id") String userId) {
        WorkItem workItem = syncService.getWorkItemById(id);
        if (workItem == null) {
            return ResponseEntity.notFound().build();
        }
        if (!syncService.hasAccessToProject(workItem.getProjectId(), userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(workItem);
    }

    @PostMapping
    public ResponseEntity<?> createWorkItem(@RequestBody WorkItem workItem,
                                            @RequestHeader("X-User-Id") String userId) {
        if (!accessService.isCustomer(workItem.getProjectId(), userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error",
                            "Прямое редактирование доступно только заказчику. " +
                                    "Сметчик должен отправить черновик на согласование."));
        }
        return ResponseEntity.ok(syncService.addWorkItem(workItem, userId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateWorkItem(@PathVariable String id,
                                            @RequestBody WorkItem workItem,
                                            @RequestHeader("X-User-Id") String userId) {
        if (!accessService.isCustomer(workItem.getProjectId(), userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Прямое редактирование доступно только заказчику."));
        }
        workItem.setId(id);
        return ResponseEntity.ok(syncService.updateWorkItem(workItem, userId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteWorkItem(@PathVariable String id,
                                            @RequestHeader("X-User-Id") String userId) {
        WorkItem existing = syncService.getWorkItemById(id);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }
        if (!accessService.isCustomer(existing.getProjectId(), userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Прямое удаление доступно только заказчику."));
        }
        syncService.deleteWorkItem(id, userId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/complete")
    public ResponseEntity<?> markAsCompleted(@PathVariable String id,
                                             @RequestHeader("X-User-Id") String userId) {
        WorkItem existing = syncService.getWorkItemById(id);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }
        if (!accessService.isCustomer(existing.getProjectId(), userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Отметить работу может только заказчик."));
        }
        WorkItem updated = syncService.markWorkItemAsCompleted(id, userId);
        return updated != null ? ResponseEntity.ok(updated) : ResponseEntity.notFound().build();
    }
}