package com.changelog.controller;

import com.changelog.config.TenantResolver;
import com.changelog.model.TaskInstance;
import com.changelog.service.OnboardingInstanceService;
import com.changelog.service.TaskInstanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class TaskController {

    private final TaskInstanceService taskService;
    private final OnboardingInstanceService instanceService;
    private final TenantResolver tenantResolver;

    @GetMapping("/onboardings/{onboardingId}/tasks")
    public ResponseEntity<List<TaskInstance>> listTasks(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID onboardingId) {
        return ResponseEntity.ok(instanceService.getInstance(onboardingId, tenantResolver.getTenantId(jwt)).getTasks());
    }

    @PostMapping("/tasks/{taskId}/complete")
    public ResponseEntity<TaskInstance> completeTask(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID taskId,
            @RequestParam UUID onboardingId) {
        String userId = jwt != null ? jwt.getSubject() : null;
        return ResponseEntity.ok(taskService.completeTask(taskId, onboardingId, userId));
    }

    @PostMapping("/tasks/{taskId}/skip")
    public ResponseEntity<TaskInstance> skipTask(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID taskId,
            @RequestParam UUID onboardingId) {
        String userId = jwt != null ? jwt.getSubject() : null;
        return ResponseEntity.ok(taskService.skipTask(taskId, onboardingId, userId));
    }
}
