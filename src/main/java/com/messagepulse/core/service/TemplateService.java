package com.messagepulse.core.service;

import com.messagepulse.core.entity.MessageTemplate;
import com.messagepulse.core.repository.MessageTemplateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class TemplateService {

    private final MessageTemplateRepository templateRepository;

    public TemplateService(MessageTemplateRepository templateRepository) {
        this.templateRepository = templateRepository;
    }

    @Transactional
    public MessageTemplate createTemplate(MessageTemplate template) {
        template.setId(UUID.randomUUID().toString());
        return templateRepository.save(template);
    }

    public MessageTemplate getTemplate(String id) {
        return templateRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Template not found: " + id));
    }

    public List<MessageTemplate> listTemplates(String tenantId, String channelType) {
        if (channelType != null) {
            return templateRepository.findByTenantIdAndChannelType(tenantId, channelType);
        }
        return templateRepository.findByTenantId(tenantId);
    }

    @Transactional
    public MessageTemplate updateTemplate(String id, MessageTemplate updated) {
        MessageTemplate existing = getTemplate(id);
        existing.setTemplateName(updated.getTemplateName());
        existing.setContent(updated.getContent());
        existing.setVariables(updated.getVariables());
        return templateRepository.save(existing);
    }

    @Transactional
    public void deleteTemplate(String id) {
        templateRepository.deleteById(id);
    }

    public String renderTemplate(String templateContent, Map<String, String> variables) {
        if (templateContent == null) {
            return "";
        }
        String result = templateContent;
        if (variables != null) {
            for (Map.Entry<String, String> entry : variables.entrySet()) {
                result = result.replace("{{" + entry.getKey() + "}}", entry.getValue());
            }
        }
        return result;
    }
}
