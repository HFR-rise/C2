package com.example.estimateserver.repository;

import com.example.estimateserver.model.SharedProject;
import com.example.estimateserver.model.ShareStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface SharedProjectRepository extends JpaRepository<SharedProject, String> {

    List<SharedProject> findBySharedWithUserId(String userId);

    List<SharedProject> findByProjectId(String projectId);

    List<SharedProject> findBySharedByUserId(String userId);

    List<SharedProject> findBySharedWithUserIdAndStatus(String userId, ShareStatus status);

    Optional<SharedProject> findByProjectIdAndSharedWithUserId(String projectId, String userId);

    boolean existsByProjectIdAndSharedWithUserId(String projectId, String userId);

    @Query("SELECT sp.projectId FROM SharedProject sp " +
            "WHERE sp.sharedWithUserId = :userId AND sp.status = :status")
    List<String> findSharedProjectIdsByUserAndStatus(@Param("userId") String userId,
                                                     @Param("status") ShareStatus status);

    @Query("SELECT sp.projectId FROM SharedProject sp WHERE sp.sharedWithUserId = :userId")
    List<String> findSharedProjectIdsByUser(@Param("userId") String userId);

    long deleteByProjectId(String projectId);

    long deleteByProjectIdIn(Collection<String> projectIds);

    long deleteByProjectIdAndSharedWithUserId(String projectId, String userId);

    long deleteBySharedWithUserId(String userId);

    long deleteBySharedByUserId(String userId);
}