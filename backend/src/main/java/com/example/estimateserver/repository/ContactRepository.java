package com.example.estimateserver.repository;

import com.example.estimateserver.model.Contact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ContactRepository extends JpaRepository<Contact, String> {

    List<Contact> findByUserId(String userId);

    @Query("SELECT DISTINCT c FROM Contact c " +
            "LEFT JOIN ContactMethod cm ON cm.contactId = c.id " +
            "WHERE c.userId = :userId " +
            "AND cm.value LIKE CONCAT('%', :phone, '%')")
    List<Contact> findByUserIdAndPhoneNumber(@Param("userId") String userId, @Param("phone") String phone);

    @Query("SELECT c FROM Contact c WHERE c.userId = :userId AND c.name LIKE CONCAT('%', :query, '%')")
    List<Contact> searchByUserIdAndName(@Param("userId") String userId, @Param("query") String query);

    @Query("SELECT c FROM Contact c WHERE c.userId = :userId ORDER BY c.name ASC")
    List<Contact> findAllByUserIdOrderByNameAsc(@Param("userId") String userId);
}