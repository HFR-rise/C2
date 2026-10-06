package com.example.estimateserver.service;

import com.example.estimateserver.dto.ProjectSnapshot;
import com.example.estimateserver.model.Material;
import com.example.estimateserver.model.Project;
import com.example.estimateserver.model.WorkItem;
import com.example.estimateserver.repository.MaterialRepository;
import com.example.estimateserver.repository.ProjectRepository;
import com.example.estimateserver.repository.WorkItemRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Service
public class SnapshotApplier {

    private final ProjectRepository projectRepository;
    private final MaterialRepository materialRepository;
    private final WorkItemRepository workItemRepository;

    public SnapshotApplier(ProjectRepository projectRepository,
                           MaterialRepository materialRepository,
                           WorkItemRepository workItemRepository) {
        this.projectRepository = projectRepository;
        this.materialRepository = materialRepository;
        this.workItemRepository = workItemRepository;
    }

    public void apply(Project project, ProjectSnapshot snapshot, String actorId) {
        String projectId = project.getId();

        if (snapshot.getName() != null && !snapshot.getName().isBlank()) {
            project.setName(snapshot.getName());
        }
        if (snapshot.getDescription() != null) {
            project.setDescription(snapshot.getDescription());
        }
        if (snapshot.getObjectId() != null) {
            project.setObjectId(snapshot.getObjectId().isBlank()
                    ? null
                    : snapshot.getObjectId());
        }

        if (snapshot.getMaterials() != null) {
            materialRepository.deleteByProjectId(projectId);

            if (!snapshot.getMaterials().isEmpty()) {
                List<Material> materials = new ArrayList<>(snapshot.getMaterials().size());
                for (ProjectSnapshot.MaterialSnapshot s : snapshot.getMaterials()) {
                    Material m = new Material();
                    m.setId(s.getId() != null ? s.getId() : UUID.randomUUID().toString());
                    m.setProjectId(projectId);
                    m.setName(s.getName());
                    m.setQuantity(s.getQuantity() != null ? s.getQuantity() : 0.0);
                    m.setUnit(s.getUnit() != null ? s.getUnit() : "шт");
                    m.setUnitPrice(s.getUnitPrice() != null ? s.getUnitPrice() : 0.0);
                    m.setCategory(s.getCategory() != null ? s.getCategory() : "");
                    m.setNotes(s.getNotes() != null ? s.getNotes() : "");
                    m.setUserId(project.getCreatedBy());
                    materials.add(m);
                }
                materialRepository.saveAll(materials);
            }
        }

        if (snapshot.getWorkItems() != null) {
            workItemRepository.deleteByProjectId(projectId);

            if (!snapshot.getWorkItems().isEmpty()) {
                List<WorkItem> works = new ArrayList<>(snapshot.getWorkItems().size());
                for (ProjectSnapshot.WorkItemSnapshot s : snapshot.getWorkItems()) {
                    WorkItem w = new WorkItem();
                    w.setId(s.getId() != null ? s.getId() : UUID.randomUUID().toString());
                    w.setProjectId(projectId);
                    w.setName(s.getName());
                    w.setStage(s.getStage() != null ? s.getStage() : 1);
                    w.setLaborHours(s.getLaborHours() != null ? s.getLaborHours() : 0.0);
                    w.setHourlyRate(s.getHourlyRate() != null ? s.getHourlyRate() : 0.0);
                    w.setMaterialCost(s.getMaterialCost() != null ? s.getMaterialCost() : 0.0);
                    w.setIsCompleted(s.getIsCompleted() != null ? s.getIsCompleted() : false);
                    w.setNotes(s.getNotes() != null ? s.getNotes() : "");
                    w.setUserId(project.getCreatedBy());
                    works.add(w);
                }
                workItemRepository.saveAll(works);
            }
        }

        Double materialCost = materialRepository.sumCostByProject(projectId);
        Double workCost = workItemRepository.sumCostByProject(projectId);
        double total = (materialCost != null ? materialCost : 0.0)
                + (workCost != null ? workCost : 0.0);
        project.setTotalBudget(total);

        project.setUpdatedAt(new Date());
        if (actorId != null) {
            project.setLastModifiedBy(actorId);
        }

        projectRepository.save(project);
    }
}