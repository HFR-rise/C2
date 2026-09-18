package com.example.estimateserver.controller;

import com.example.estimateserver.model.WorkItem;
import com.example.estimateserver.service.SyncService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/work-items")
@CrossOrigin(origins = "*")
public class WorkItemController {

    private final SyncService syncService;

    public WorkItemController(SyncService syncService) {
        this.syncService = syncService;
    }

    @GetMapping("/project/{projectId}")
    public ResponseEntity<List<WorkItem>> getWorkItemsByProject(@PathVariable String projectId) {
        return ResponseEntity.ok(syncService.getWorkItems(projectId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<WorkItem> getWorkItemById(@PathVariable String id) {
        WorkItem workItem = syncService.getWorkItemById(id);
        return workItem != null ? ResponseEntity.ok(workItem) : ResponseEntity.notFound().build();
    }

    @PostMapping
    public ResponseEntity<WorkItem> createWorkItem(@RequestBody WorkItem workItem,
                                                   @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(syncService.addWorkItem(workItem, userId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<WorkItem> updateWorkItem(@PathVariable String id,
                                                   @RequestBody WorkItem workItem,
                                                   @RequestHeader("X-User-Id") String userId) {
        workItem.setId(id);
        return ResponseEntity.ok(syncService.updateWorkItem(workItem, userId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteWorkItem(@PathVariable String id,
                                               @RequestHeader("X-User-Id") String userId) {
        syncService.deleteWorkItem(id, userId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/complete")
    public ResponseEntity<WorkItem> markAsCompleted(@PathVariable String id,
                                                    @RequestHeader("X-User-Id") String userId) {
        WorkItem updated = syncService.markWorkItemAsCompleted(id, userId);
        return updated != null ? ResponseEntity.ok(updated) : ResponseEntity.notFound().build();
    }
}