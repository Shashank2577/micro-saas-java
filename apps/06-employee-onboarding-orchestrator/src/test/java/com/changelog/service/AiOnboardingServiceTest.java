package com.changelog.service;

import com.changelog.ai.AiService;
import com.changelog.model.OnboardingTemplate;
import com.changelog.model.TemplateTask;
import com.changelog.repository.OnboardingTemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiOnboardingServiceTest {

    @Mock
    private OnboardingTemplateRepository templateRepository;

    @Mock
    private AiService aiService;

    @InjectMocks
    private AiOnboardingService aiOnboardingService;

    private UUID tenantId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
    }

    @Test
    void generatePlan_Success() {
        String mockJsonResponse = """
            [
                {"title": "Task 1", "description": "Desc 1", "taskType": "read", "assigneeType": "new_hire", "dueDayOffset": 0, "isRequired": true},
                {"title": "Task 2", "description": "Desc 2", "taskType": "complete", "assigneeType": "it", "dueDayOffset": 0, "isRequired": true}
            ]
            """;
        when(aiService.callLlmRaw(anyString())).thenReturn(mockJsonResponse);
        when(templateRepository.save(any(OnboardingTemplate.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OnboardingTemplate template = aiOnboardingService.generatePlan(tenantId, "Software Engineer", "Engineering", userId);

        assertThat(template).isNotNull();
        assertThat(template.getTasks()).hasSize(2);
        assertThat(template.getTasks().get(0).getTitle()).isEqualTo("Task 1");
        verify(aiService).callLlmRaw(anyString());
        verify(templateRepository).save(any(OnboardingTemplate.class));
    }

    @Test
    void generatePlan_Fallback() {
        when(aiService.callLlmRaw(anyString())).thenThrow(new RuntimeException("LLM down"));
        when(templateRepository.save(any(OnboardingTemplate.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OnboardingTemplate template = aiOnboardingService.generatePlan(tenantId, "Software Engineer", "Engineering", userId);

        assertThat(template).isNotNull();
        assertThat(template.getTasks()).hasSize(3); // defaultTasks size
        assertThat(template.getTasks().get(0).getTitle()).isEqualTo("Welcome to the Company");
        verify(aiService).callLlmRaw(anyString());
        verify(templateRepository).save(any(OnboardingTemplate.class));
    }

    @Test
    void rewriteDescriptions_Success() {
        UUID templateId = UUID.randomUUID();
        OnboardingTemplate template = OnboardingTemplate.builder()
                .id(templateId)
                .tenantId(tenantId)
                .build();
        TemplateTask task = TemplateTask.builder()
                .title("Old Title")
                .description("Old Description")
                .template(template)
                .build();
        template.getTasks().add(task);

        when(templateRepository.findByIdAndTenantId(templateId, tenantId)).thenReturn(Optional.of(template));
        when(aiService.callLlmRaw(anyString())).thenReturn("Rewritten Description");
        when(templateRepository.save(any(OnboardingTemplate.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OnboardingTemplate result = aiOnboardingService.rewriteDescriptions(templateId, tenantId);

        assertThat(result.getTasks().get(0).getDescription()).isEqualTo("Rewritten Description");
        verify(aiService).callLlmRaw(anyString());
    }

    @Test
    void rewriteDescriptions_Fallback() {
        UUID templateId = UUID.randomUUID();
        OnboardingTemplate template = OnboardingTemplate.builder()
                .id(templateId)
                .tenantId(tenantId)
                .build();
        TemplateTask task = TemplateTask.builder()
                .title("Old Title")
                .description("Old Description")
                .template(template)
                .build();
        template.getTasks().add(task);

        when(templateRepository.findByIdAndTenantId(templateId, tenantId)).thenReturn(Optional.of(template));
        when(aiService.callLlmRaw(anyString())).thenThrow(new RuntimeException("LLM down"));
        when(templateRepository.save(any(OnboardingTemplate.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OnboardingTemplate result = aiOnboardingService.rewriteDescriptions(templateId, tenantId);

        assertThat(result.getTasks().get(0).getDescription()).isEqualTo("Old Description");
        verify(aiService).callLlmRaw(anyString());
    }
}
