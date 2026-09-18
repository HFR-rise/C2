package com.example.estimateserver.repository;

import com.example.estimateserver.model.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface ProjectRepository extends JpaRepository<Project, String> {

    List<Project> findByUserId(String userId);

    @Query("SELECT p FROM Project p WHERE p.userId = :userId ORDER BY p.updatedAt DESC")
    List<Project> findAllByUserIdOrderByUpdatedAtDesc(@Param("userId") String userId);

    List<Project> findByObjectId(String objectId);

    List<Project> findByUserIdAndObjectId(String userId, String objectId);

    @Query("SELECT p FROM Project p WHERE p.objectId IN :objectIds")
    List<Project> findAllByObjectIdIn(@Param("objectIds") Collection<String> objectIds);

    @Query("SELECT DISTINCT p FROM Project p " +
            "WHERE p.userId = :userId " +
            "OR p.id IN (SELECT sp.projectId FROM SharedProject sp " +
            "            WHERE sp.sharedWithUserId = :userId)")
    List<Project> findAllAccessibleForUser(@Param("userId") String userId);

    @Query("SELECT DISTINCT p FROM Project p " +
            "WHERE p.id IN (SELECT sp.projectId FROM SharedProject sp " +
            "               WHERE sp.sharedWithUserId = :userId)")
    List<Project> findSharedWithUser(@Param("userId") String userId);

    @Query("SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END FROM Project p " +
            "WHERE p.id = :projectId AND (" +
            "   p.userId = :userId " +
            "   OR p.id IN (SELECT sp.projectId FROM SharedProject sp " +
            "               WHERE sp.projectId = :projectId AND sp.sharedWithUserId = :userId)" +
            ")")
    boolean hasUserAccessToProject(@Param("projectId") String projectId,
                                   @Param("userId") String userId);

    @Query("SELECT DISTINCT p FROM Project p " +
            "LEFT JOIN Contact c ON c.id = p.customerContactId " +
            "                   OR c.id = p.foremanContactId " +
            "                   OR c.id = p.managerContactId " +
            "LEFT JOIN ContactMethod cm ON cm.contactId = c.id " +
            "WHERE p.userId = :userId " +
            "  AND cm.value LIKE CONCAT('%', :phoneNumber, '%')")
    List<Project> findProjectsByContactPhoneForUser(@Param("phoneNumber") String phoneNumber,
                                                    @Param("userId") String userId);

    @Modifying
    @Query("UPDATE Project p SET p.totalBudget = :total WHERE p.id = :id")
    int updateTotalBudget(@Param("id") String id, @Param("total") double total);

    @Modifying
    @Query("UPDATE Project p SET p.objectId = :objectId, p.updatedAt = CURRENT_TIMESTAMP " +
            "WHERE p.id = :id")
    int updateObjectId(@Param("id") String id, @Param("objectId") String objectId);
}