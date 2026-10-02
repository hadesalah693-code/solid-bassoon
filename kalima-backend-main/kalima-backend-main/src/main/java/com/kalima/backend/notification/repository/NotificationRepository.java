package com.kalima.backend.notification.repository;

import com.kalima.backend.notification.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    @EntityGraph(attributePaths = "caseFile")
    @Query("""
            SELECT n FROM Notification n
            WHERE n.recipientUser.id = :recipientId
            """)
    Page<Notification> findByRecipientId(
            @Param("recipientId") UUID recipientId, Pageable pageable);

    long countByRecipientUser_IdAndReadIsFalse(UUID recipientId);

    @Query("""
            SELECT n FROM Notification n
            WHERE n.id = :id AND n.recipientUser.id = :recipientId
            """)
    Optional<Notification> findByIdForRecipient(
            @Param("id") UUID id, @Param("recipientId") UUID recipientId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Notification n
            SET n.read = true, n.readAt = :readAt
            WHERE n.recipientUser.id = :recipientId
              AND n.read = false
            """)
    int markAllReadForRecipient(
            @Param("recipientId") UUID recipientId, @Param("readAt") LocalDateTime readAt);
}
