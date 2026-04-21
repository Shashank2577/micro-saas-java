package com.changelog.approval.service;

import com.changelog.approval.model.WorkflowTemplate;
import com.changelog.approval.model.WorkflowTemplateStep;
import com.changelog.approval.repository.WorkflowTemplateRepository;
import com.changelog.config.TenantResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import com.changelog.exception.EntityNotFoundException;
import com.changelog.exception.ForbiddenException;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WorkflowTemplateService {

    private final WorkflowTemplateRepository templateRepository;
    private final TenantResolver tenantResolver;

    private Jwt getCurrentJwt() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof Jwt) {
            return (Jwt) principal;
        }
        return null;
    }

    private UUID getCurrentTenantId() {
        return tenantResolver.getTenantId(getCurrentJwt());
    }

    private UUID getCurrentUserId() {
        return tenantResolver.getUserId(getCurrentJwt());
    }

    private void verifyTenant(UUID tenantId) {
        if (!getCurrentTenantId().equals(tenantId)) {
            throw new ForbiddenException("Access Denied");
        }
    }

    @Transactional
    public WorkflowTemplate createTemplate(WorkflowTemplate template) {
        template.setTenantId(getCurrentTenantId());
        template.setCreatedBy(getCurrentUserId());
        
        if (template.getSteps() != null) {
            for (WorkflowTemplateStep step : template.getSteps()) {
                step.setTemplate(template);
            }
        }
        
        return templateRepository.save(template);
    }

    @Transactional(readOnly = true)
    public WorkflowTemplate getTemplate(UUID templateId) {
        WorkflowTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> new EntityNotFoundException("Template not found"));
        verifyTenant(template.getTenantId());
        return template;
    }

    @Transactional
    public WorkflowTemplate updateTemplate(UUID templateId, WorkflowTemplate updatedTemplate) {
        WorkflowTemplate existing = getTemplate(templateId);
        
        existing.setName(updatedTemplate.getName());
        existing.setDescription(updatedTemplate.getDescription());
        
        // Replace steps
        existing.getSteps().clear();
        if (updatedTemplate.getSteps() != null) {
            for (WorkflowTemplateStep step : updatedTemplate.getSteps()) {
                step.setTemplate(existing);
                existing.getSteps().add(step);
            }
        }
        
        return templateRepository.save(existing);
    }

    @Transactional(readOnly = true)
    public List<WorkflowTemplate> listTemplates() {
        return templateRepository.findByTenantIdAndIsActiveTrue(getCurrentTenantId());
    }

    @Transactional
    public void archiveTemplate(UUID templateId) {
        WorkflowTemplate existing = getTemplate(templateId);
        existing.setIsActive(false);
        templateRepository.save(existing);
    }
}
