package com.changelog.controller;

import com.changelog.model.TaskInstance;
import com.changelog.service.OnboardingInstanceService;
import com.changelog.service.TaskInstanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class TaskController {

    private final TaskInstanceService taskService;
    private final OnboardingInstanceService instanceService;

    @GetMapping("/onboardings/{onboardingId}/tasks")
    public ResponseEntity<List<TaskInstance>> listTasks(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @PathVariable UUID onboardingId) {
        return ResponseEntity.ok(instanceService.getInstance(onboardingId, tenantId).getTasks());
    }

    @PostMapping("/tasks/{taskId}/complete")
    public ResponseEntity<TaskInstance> completeTask(
            @RequestHeader("X-User-ID") String userId,
            @PathVariable UUID taskId,
            @RequestParam UUID onboardingId) {
        return ResponseEntity.ok(taskService.completeTask(taskId, onboardingId, userId));
    }

    @PostMapping("/tasks/{taskId}/skip")
    public ResponseEntity<TaskInstance> skipTask(
            @RequestHeader("X-User-ID") String userId,
            @PathVariable UUID taskId,
            @RequestParam UUID onboardingId) {
        return ResponseEntity.ok(taskService.skipTask(taskId, onboardingId, userId));
    }
}
