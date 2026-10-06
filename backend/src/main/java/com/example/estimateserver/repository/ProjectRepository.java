package com.example.estimateserver.repository;

import com.example.estimateserver.model.Project;
import com.example.estimateserver.model.ProjectRole;
import com.example.estimateserver.model.ProjectState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface ProjectRepository extends JpaRepository<Project, String> {

    List<Project> findByObjectId(String objectId);

    @Query("SELECT p FROM Project p WHERE p.objectId IN :objectIds")
    List<Project> findAllByObjectIdIn(@Param("objectIds") Collection<String> objectIds);

    @Query("SELECT DISTINCT p FROM Project p " +
            "WHERE p.id IN (SELECT pm.projectId FROM ProjectMember pm " +
            "               WHERE pm.userId = :userId)")
    List<Project> findAllAccessibleForUser(@Param("userId") String userId);

    @Query("SELECT DISTINCT p FROM Project p " +
            "WHERE p.id IN (SELECT pm.projectId FROM ProjectMember pm " +
            "               WHERE pm.userId = :userId AND pm.role = :role)")
    List<Project> findAllForUserWithRole(@Param("userId") String userId,
                                         @Param("role") ProjectRole role);

    @Query("SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END FROM Project p " +
            "WHERE p.id = :projectId AND EXISTS (" +
            "   SELECT 1 FROM ProjectMember pm " +
            "   WHERE pm.projectId = p.id AND pm.userId = :userId)")
    boolean hasUserAccessToProject(@Param("projectId") String projectId,
                                   @Param("userId") String userId);

    @Query("SELECT p.state FROM Project p WHERE p.id = :id")
    ProjectState findStateById(@Param("id") String id);

    @Modifying
    @Query("UPDATE Project p SET p.state = :state, p.updatedAt = CURRENT_TIMESTAMP " +
            "WHERE p.id = :id")
    int updateState(@Param("id") String id, @Param("state") ProjectState state);

    @Modifying
    @Query("UPDATE Project p SET p.hasPendingChanges = :has, " +
            "p.updatedAt = CURRENT_TIMESTAMP WHERE p.id = :id")
    int updateHasPendingChanges(@Param("id") String id, @Param("has") boolean has);

    @Modifying
    @Query("UPDATE Project p SET p.totalBudget = :total WHERE p.id = :id")
    int updateTotalBudget(@Param("id") String id, @Param("total") double total);

    @Modifying
    @Query("UPDATE Project p SET p.objectId = :objectId, p.updatedAt = CURRENT_TIMESTAMP " +
            "WHERE p.id = :id")
    int updateObjectId(@Param("id") String id, @Param("objectId") String objectId);
}