package com.example.estimateserver.repository;

import com.example.estimateserver.model.WorkItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface WorkItemRepository extends JpaRepository<WorkItem, String> {

    List<WorkItem> findByProjectId(String projectId);

    List<WorkItem> findByUserId(String userId);

    List<WorkItem> findByProjectIdIn(Collection<String> projectIds);

    @Query("SELECT COALESCE(SUM(w.laborHours * w.hourlyRate + w.materialCost), 0) " +
            "FROM WorkItem w WHERE w.projectId = :projectId")
    Double sumCostByProject(@Param("projectId") String projectId);

    long deleteByProjectId(String projectId);

    long deleteByProjectIdIn(Collection<String> projectIds);
}