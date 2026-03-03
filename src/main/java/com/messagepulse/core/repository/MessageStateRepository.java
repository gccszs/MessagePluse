package com.messagepulse.core.repository;

import com.messagepulse.core.entity.MessageState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageStateRepository extends JpaRepository<MessageState, Long> {

    List<MessageState> findByMessageIdOrderByCreatedAtDesc(String messageId);

    List<MessageState> findByMessageIdAndChannelType(String messageId, String channelType);
}
