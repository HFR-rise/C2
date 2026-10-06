package com.example.estimateserver.repository;

import com.example.estimateserver.model.ChangeKind;
import com.example.estimateserver.model.ChangeStatus;
import com.example.estimateserver.model.ProjectChangeRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectChangeRequestRepository
        extends JpaRepository<ProjectChangeRequest, String> {

    List<ProjectChangeRequest> findByProjectId(String projectId);

    List<ProjectChangeRequest> findByProjectIdOrderByCreatedAtDesc(String projectId);

    List<ProjectChangeRequest> findByProjectIdAndStatus(String projectId, ChangeStatus status);

    List<ProjectChangeRequest> findByProjectIdAndStatusOrderByCreatedAtDesc(
            String projectId, ChangeStatus status);

    List<ProjectChangeRequest> findByProjectIdAndKind(String projectId, ChangeKind kind);

    List<ProjectChangeRequest> findByAuthorId(String authorId);

    List<ProjectChangeRequest> findByAuthorIdAndStatusOrderByCreatedAtDesc(
            String authorId, ChangeStatus status);

    Optional<ProjectChangeRequest> findByIdAndStatus(String id, ChangeStatus status);

    Optional<ProjectChangeRequest> findFirstByProjectIdAndKindAndStatusOrderByCreatedAtDesc(
            String projectId, ChangeKind kind, ChangeStatus status);

    List<ProjectChangeRequest> findByProjectIdAndKindOrderByCreatedAtDesc(
            String projectId, ChangeKind kind);

    boolean existsByProjectIdAndKindAndStatus(
            String projectId, ChangeKind kind, ChangeStatus status);

    long deleteByProjectIdAndStatus(String projectId, ChangeStatus status);

    boolean existsByProjectIdAndStatus(String projectId, ChangeStatus status);

    long deleteByProjectId(String projectId);

    long deleteByProjectIdIn(Collection<String> projectIds);

    @Query("SELECT r FROM ProjectChangeRequest r " +
            "WHERE r.projectId IN :projectIds " +
            "ORDER BY r.createdAt DESC")
    List<ProjectChangeRequest> findAllForProjects(@Param("projectIds") Collection<String> projectIds);

    @Query("SELECT r FROM ProjectChangeRequest r " +
            "WHERE r.projectId = :projectId " +
            "AND (r.status = com.example.estimateserver.model.ChangeStatus.PENDING " +
            "     OR r.reviewedAt >= :since) " +
            "ORDER BY r.createdAt DESC")
    List<ProjectChangeRequest> findRecentForProject(@Param("projectId") String projectId,
                                                    @Param("since") java.util.Date since);
}