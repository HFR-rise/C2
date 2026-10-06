package com.example.estimateserver.repository;

import com.example.estimateserver.model.ProjectMember;
import com.example.estimateserver.model.ProjectRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectMemberRepository extends JpaRepository<ProjectMember, String> {

    List<ProjectMember> findByProjectId(String projectId);

    List<ProjectMember> findByUserId(String userId);

    List<ProjectMember> findByProjectIdIn(Collection<String> projectIds);   // <-- новое

    List<ProjectMember> findByProjectIdAndRole(String projectId, ProjectRole role);

    Optional<ProjectMember> findByProjectIdAndUserId(String projectId, String userId);

    Optional<ProjectMember> findFirstByProjectIdAndRole(String projectId, ProjectRole role);

    boolean existsByProjectIdAndUserId(String projectId, String userId);

    boolean existsByProjectIdAndRole(String projectId, ProjectRole role);

    long countByProjectIdAndRole(String projectId, ProjectRole role);

    long deleteByProjectId(String projectId);

    long deleteByProjectIdIn(Collection<String> projectIds);

    long deleteByProjectIdAndUserId(String projectId, String userId);

    @Query("SELECT pm.userId FROM ProjectMember pm WHERE pm.projectId = :projectId")
    List<String> findUserIdsByProjectId(@Param("projectId") String projectId);

    @Query("SELECT pm.projectId FROM ProjectMember pm WHERE pm.userId = :userId")
    List<String> findProjectIdsByUserId(@Param("userId") String userId);

    @Query("SELECT pm.projectId FROM ProjectMember pm " +
            "WHERE pm.userId = :userId AND pm.role = :role")
    List<String> findProjectIdsByUserIdAndRole(@Param("userId") String userId,
                                               @Param("role") ProjectRole role);
}