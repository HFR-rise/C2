package com.example.estimateserver.controller;

import com.example.estimateserver.model.Material;
import com.example.estimateserver.service.AccessDeniedException;
import com.example.estimateserver.service.ProjectAccessService;
import com.example.estimateserver.service.SyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/materials")
@CrossOrigin(origins = "*")
public class MaterialController {

    private static final Logger log = LoggerFactory.getLogger(MaterialController.class);

    private final SyncService syncService;
    private final ProjectAccessService accessService;

    public MaterialController(SyncService syncService,
                              ProjectAccessService accessService) {
        this.syncService = syncService;
        this.accessService = accessService;
    }

    @PostMapping
    public ResponseEntity<?> addMaterial(@RequestBody Material material,
                                         @RequestHeader("X-User-Id") String userId) {
        if (!isCustomer(material.getProjectId(), userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error",
                            "Изменять материалы проекта может только заказчик. " +
                                    "Попросите заказчика внести правки или отправить их на согласование."));
        }
        return ResponseEntity.ok(syncService.addMaterial(material, userId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateMaterial(@PathVariable String id,
                                            @RequestBody Material material,
                                            @RequestHeader("X-User-Id") String userId) {
        if (!isCustomer(material.getProjectId(), userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error",
                            "Изменять материалы проекта может только заказчик."));
        }
        material.setId(id);
        return ResponseEntity.ok(syncService.updateMaterial(material, userId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteMaterial(@PathVariable String id,
                                            @RequestHeader("X-User-Id") String userId) {
        Material existing = syncService.getMaterialById(id);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }
        if (!isCustomer(existing.getProjectId(), userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error",
                            "Удалять материалы может только заказчик."));
        }
        syncService.deleteMaterial(id, userId);
        return ResponseEntity.ok().build();
    }

    private boolean isCustomer(String projectId, String userId) {
        if (projectId == null) return false;
        return accessService.isCustomer(projectId, userId);
    }
}