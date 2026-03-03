package com.messagepulse.core.repository;

import com.messagepulse.core.entity.Message;
import com.messagepulse.core.enums.MessageStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface MessageRepository extends JpaRepository<Message, String> {

    Optional<Message> findByMessageId(String messageId);

    List<Message> findByTenantIdAndStatus(String tenantId, MessageStatus status);

    List<Message> findByStatusAndCreatedAtBefore(MessageStatus status, LocalDateTime before);

    boolean existsByMessageId(String messageId);
}
