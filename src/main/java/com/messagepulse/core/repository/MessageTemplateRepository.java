package com.messagepulse.core.repository;

import com.messagepulse.core.entity.MessageTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MessageTemplateRepository extends JpaRepository<MessageTemplate, String> {

    Optional<MessageTemplate> findByTenantIdAndTemplateNameAndChannelType(String tenantId, String templateName, String channelType);

    List<MessageTemplate> findByTenantIdAndChannelType(String tenantId, String channelType);

    List<MessageTemplate> findByTenantId(String tenantId);
}
