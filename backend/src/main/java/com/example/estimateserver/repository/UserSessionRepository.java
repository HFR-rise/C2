package com.example.estimateserver.repository;

import com.example.estimateserver.model.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserSessionRepository extends JpaRepository<UserSession, String> {

    Optional<UserSession> findByUserId(String userId);

    List<UserSession> findAllByActiveTrue();

    @Modifying
    @Transactional
    @Query("UPDATE UserSession us SET us.active = false WHERE us.userId = :userId")
    void deactivateUserSession(@Param("userId") String userId);

    @Modifying
    @Transactional
    @Query("DELETE FROM UserSession us WHERE us.active = false AND us.lastPongAt < :timeout")
    void deleteInactiveSessions(@Param("timeout") Date timeout);
}
