package com.example.estimateserver.repository;

import com.example.estimateserver.model.ObjectModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ObjectRepository extends JpaRepository<ObjectModel, String> {

    List<ObjectModel> findByParentObjectIdIsNull();
    List<ObjectModel> findByParentObjectId(String parentId);

    @Query("SELECT o FROM ObjectModel o WHERE o.parentObjectId IS NULL AND o.userId = :userId")
    List<ObjectModel> findByParentObjectIdIsNullAndUserId(@Param("userId") String userId);

    @Query("SELECT o FROM ObjectModel o WHERE o.parentObjectId = :parentId AND o.userId = :userId")
    List<ObjectModel> findByParentObjectIdAndUserId(@Param("parentId") String parentId, @Param("userId") String userId);

    @Query("SELECT o FROM ObjectModel o WHERE o.id = :id AND o.userId = :userId")
    Optional<ObjectModel> findByIdAndUserId(@Param("id") String id, @Param("userId") String userId);

    @Query("SELECT o FROM ObjectModel o WHERE o.userId = :userId")
    List<ObjectModel> findAllByUserId(@Param("userId") String userId);

    @Query("SELECT o FROM ObjectModel o WHERE o.userId = :userId AND o.name LIKE CONCAT('%', :query, '%')")
    List<ObjectModel> searchByUserIdAndName(@Param("userId") String userId, @Param("query") String query);
}