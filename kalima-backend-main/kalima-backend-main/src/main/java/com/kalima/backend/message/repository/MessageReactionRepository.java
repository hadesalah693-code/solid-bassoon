package com.kalima.backend.message.repository;

import com.kalima.backend.domain.entity.MessageReaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MessageReactionRepository extends JpaRepository<MessageReaction, UUID> {

    @Query("SELECT r FROM MessageReaction r WHERE r.message.id IN :messageIds")
    List<MessageReaction> findByMessageIdIn(@Param("messageIds") List<UUID> messageIds);

    Optional<MessageReaction> findByMessage_IdAndPerson_IdAndEmoji(
            UUID messageId, UUID personId, String emoji);

    @Modifying
    @Query("DELETE FROM MessageReaction r WHERE r.message.id = :messageId AND r.person.id = :personId AND r.emoji = :emoji")
    void deleteByMessage_IdAndPerson_IdAndEmoji(
            @Param("messageId") UUID messageId,
            @Param("personId") UUID personId,
            @Param("emoji") String emoji);
}
