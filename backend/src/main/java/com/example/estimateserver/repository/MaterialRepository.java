package com.example.estimateserver.repository;

import com.example.estimateserver.model.Material;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface MaterialRepository extends JpaRepository<Material, String> {

    List<Material> findByProjectId(String projectId);

    List<Material> findByUserId(String userId);

    List<Material> findByProjectIdIn(Collection<String> projectIds);

    @Query("SELECT COALESCE(SUM(m.quantity * m.unitPrice), 0) " +
            "FROM Material m WHERE m.projectId = :projectId")
    Double sumCostByProject(@Param("projectId") String projectId);

    long deleteByProjectId(String projectId);

    long deleteByProjectIdIn(Collection<String> projectIds);
}
