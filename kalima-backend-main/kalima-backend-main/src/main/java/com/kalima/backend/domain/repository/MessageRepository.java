package com.kalima.backend.domain.repository;

import com.kalima.backend.domain.entity.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<Message, UUID> {

    @EntityGraph(attributePaths = {"caseFile", "senderUser", "receiverUser"})
    @Query("SELECT m FROM Message m WHERE m.id = :id")
    Optional<Message> findScopedById(@Param("id") UUID id);

    @EntityGraph(attributePaths = {"senderUser", "receiverUser", "caseFile"})
    @Query("""
            SELECT m FROM Message m
            WHERE m.caseFile.id = :caseId
              AND (m.senderUser.id = :personId OR m.receiverUser.id = :personId)
            ORDER BY m.createdAt ASC
            """)
    List<Message> findConversationForParticipant(
            @Param("caseId") UUID caseId,
            @Param("personId") UUID personId);

    @EntityGraph(attributePaths = {"senderUser", "receiverUser", "caseFile"})
    @Query("""
            SELECT m FROM Message m
            WHERE m.caseFile.id = :caseId
            ORDER BY m.createdAt ASC
            """)
    List<Message> findConversationForStaff(@Param("caseId") UUID caseId);

    @EntityGraph(attributePaths = {"senderUser", "receiverUser", "caseFile"})
    @Query("""
            SELECT m FROM Message m
            WHERE m.caseFile.id = :caseId
            ORDER BY m.createdAt DESC, m.id DESC
            """)
    List<Message> findStaffLatestPage(
            @Param("caseId") UUID caseId,
            Pageable pageable);

    @EntityGraph(attributePaths = {"senderUser", "receiverUser", "caseFile"})
    @Query("""
            SELECT m FROM Message m
            WHERE m.caseFile.id = :caseId
              AND (m.createdAt < :beforeAt
                   OR (m.createdAt = :beforeAt AND m.id < :beforeId))
            ORDER BY m.createdAt DESC, m.id DESC
            """)
    List<Message> findStaffOlderPage(
            @Param("caseId") UUID caseId,
            @Param("beforeAt") LocalDateTime beforeAt,
            @Param("beforeId") UUID beforeId,
            Pageable pageable);

    @EntityGraph(attributePaths = {"senderUser", "receiverUser", "caseFile"})
    @Query("""
            SELECT m FROM Message m
            WHERE m.caseFile.id = :caseId
              AND (m.senderUser.id = :personId OR m.receiverUser.id = :personId)
            ORDER BY m.createdAt DESC, m.id DESC
            """)
    List<Message> findParticipantLatestPage(
            @Param("caseId") UUID caseId,
            @Param("personId") UUID personId,
            Pageable pageable);

    @EntityGraph(attributePaths = {"senderUser", "receiverUser", "caseFile"})
    @Query("""
            SELECT m FROM Message m
            WHERE m.caseFile.id = :caseId
              AND (m.senderUser.id = :personId OR m.receiverUser.id = :personId)
              AND (m.createdAt < :beforeAt
                   OR (m.createdAt = :beforeAt AND m.id < :beforeId))
            ORDER BY m.createdAt DESC, m.id DESC
            """)
    List<Message> findParticipantOlderPage(
            @Param("caseId") UUID caseId,
            @Param("personId") UUID personId,
            @Param("beforeAt") LocalDateTime beforeAt,
            @Param("beforeId") UUID beforeId,
            Pageable pageable);

    @Modifying
    @Query("""
            UPDATE Message m
            SET m.read = true, m.readAt = :readAt
            WHERE m.caseFile.id = :caseId
              AND m.receiverUser.id = :receiverId
              AND m.read = false
            """)
    int markReadForReceiver(
            @Param("caseId") UUID caseId,
            @Param("receiverId") UUID receiverId,
            @Param("readAt") LocalDateTime readAt);

    /**
     * For each case in the given set, returns the single most-recent message.
     * Uses a correlated NOT EXISTS to avoid DISTINCT ON (JPQL portable).
     */
    @EntityGraph(attributePaths = {"senderUser", "receiverUser"})
    @Query("""
            SELECT m FROM Message m
            WHERE m.caseFile.id IN :caseIds
              AND NOT EXISTS (
                  SELECT 1 FROM Message m2
                  WHERE m2.caseFile.id = m.caseFile.id
                    AND m2.createdAt > m.createdAt
              )
            ORDER BY m.createdAt DESC
            """)
    List<Message> findLastMessagesPerCase(@Param("caseIds") List<UUID> caseIds);

    /**
     * Returns [caseId, unreadCount] pairs for messages where the given person
     * is the receiver and the message has not been read yet.
     */
    @Query("""
            SELECT m.caseFile.id, COUNT(m)
            FROM Message m
            WHERE m.caseFile.id IN :caseIds
              AND m.receiverUser.id = :receiverId
              AND m.read = false
            GROUP BY m.caseFile.id
            """)
    List<Object[]> countUnreadPerCase(
            @Param("caseIds") List<UUID> caseIds,
            @Param("receiverId") UUID receiverId);
}
