package com.kalima.backend.domain.entity;

import com.kalima.backend.casefile.entity.CaseFile;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "messages")
@Getter
@Setter
@NoArgsConstructor
@Filter(name = "tenantFilter", condition = "company_id = :companyId")
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false, updatable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "case_id", nullable = false)
    private CaseFile caseFile;

    /**
     * Author of the message. {@code NULL} only when {@link #senderType} is
     * {@link DomainEnums.MessageSenderType#BOT}. The DB CHECK constraint
     * {@code chk_messages_sender_user_match_type} enforces the relationship.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_user_id")
    private Person senderUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiver_user_id", nullable = false)
    private Person receiverUser;

    @Enumerated(EnumType.STRING)
    @Column(name = "sender_type", nullable = false, length = 10)
    private DomainEnums.MessageSenderType senderType = DomainEnums.MessageSenderType.USER;

    @Column(name = "content", nullable = false)
    private String content;

    @Column(name = "attachment_url")
    private String attachmentUrl;

    /** The message this is a reply to; {@code null} for top-level messages. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reply_to_message_id")
    private Message replyToMessage;

    @Column(name = "is_read", nullable = false)
    private boolean read = false;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.company == null) {
            throw new IllegalStateException(
                    "Message.company must be set before persist.");
        }
        if (this.senderType == null) {
            this.senderType = DomainEnums.MessageSenderType.USER;
        }
        // Defensive invariants that mirror the DB CHECK constraints. Failing
        // fast in Java gives a clearer stack trace than a constraint violation.
        if (this.senderType == DomainEnums.MessageSenderType.USER
                && this.senderUser == null) {
            throw new IllegalStateException(
                    "Message.senderUser must be set when senderType=USER.");
        }
        if (this.senderType == DomainEnums.MessageSenderType.BOT
                && this.senderUser != null) {
            throw new IllegalStateException(
                    "Message.senderUser must be null when senderType=BOT.");
        }
    }
}
