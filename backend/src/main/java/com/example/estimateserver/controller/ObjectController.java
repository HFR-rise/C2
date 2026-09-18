package com.example.estimateserver.controller;

import com.example.estimateserver.model.ObjectModel;
import com.example.estimateserver.service.SyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/objects")
@CrossOrigin(origins = "*")
public class ObjectController {

    private static final Logger log = LoggerFactory.getLogger(ObjectController.class);

    private final SyncService syncService;

    public ObjectController(SyncService syncService) {
        this.syncService = syncService;
    }

    @GetMapping("/root")
    public ResponseEntity<List<ObjectModel>> getRootObjects(@RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(syncService.getRootObjectsForUser(userId));
    }

    @GetMapping("/{parentId}/children")
    public ResponseEntity<List<ObjectModel>> getChildObjects(@PathVariable String parentId,
                                                             @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(syncService.getChildObjectsForUser(parentId, userId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ObjectModel> getObjectById(@PathVariable String id,
                                                     @RequestHeader("X-User-Id") String userId) {
        ObjectModel object = syncService.getObjectById(id);
        if (object == null) {
            return ResponseEntity.notFound().build();
        }
        if (!isOwner(object, userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(object);
    }

    @PostMapping
    public ResponseEntity<ObjectModel> createObject(@RequestBody ObjectModel object,
                                                    @RequestHeader("X-User-Id") String userId) {
        object.setUserId(null);

        ObjectModel created = syncService.createObject(object, userId);
        log.info("Object created: {} (parent: {})", created.getId(), created.getParentObjectId());

        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ObjectModel> updateObject(@PathVariable String id,
                                                    @RequestBody ObjectModel object,
                                                    @RequestHeader("X-User-Id") String userId) {
        ObjectModel existing = syncService.getObjectById(id);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }
        if (!isOwner(existing, userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        object.setId(id);
        object.setUserId(null);

        ObjectModel updated = syncService.updateObject(object, userId);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteObject(@PathVariable String id,
                                             @RequestHeader("X-User-Id") String userId) {
        ObjectModel existing = syncService.getObjectById(id);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }
        if (!isOwner(existing, userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        syncService.deleteObject(id, userId);
        return ResponseEntity.noContent().build();
    }

    private static boolean isOwner(ObjectModel object, String userId) {
        return object.getUserId() != null && object.getUserId().equals(userId);
    }
}