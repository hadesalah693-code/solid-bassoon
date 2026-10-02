package com.kalima.backend.message.repository;

import com.kalima.backend.domain.entity.MessageAttachment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface MessageAttachmentRepository extends JpaRepository<MessageAttachment, UUID> {

    @EntityGraph(attributePaths = {"caseFile", "uploadedBy"})
    @Query("SELECT a FROM MessageAttachment a WHERE a.storedFilename = :storedFilename")
    Optional<MessageAttachment> findScopedByStoredFilename(@Param("storedFilename") String storedFilename);
}
