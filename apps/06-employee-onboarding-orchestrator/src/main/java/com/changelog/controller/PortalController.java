package com.changelog.controller;

import com.changelog.model.OnboardingInstance;
import com.changelog.model.TaskInstance;
import com.changelog.service.OnboardingInstanceService;
import com.changelog.service.TaskInstanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/portal")
@RequiredArgsConstructor
public class PortalController {

    private final OnboardingInstanceService instanceService;
    private final TaskInstanceService taskService;

    @GetMapping("/{token}")
    public ResponseEntity<List<TaskInstance>> getPortalTasks(@PathVariable String token) {
        OnboardingInstance instance = instanceService.getInstanceByToken(token);
        if (instance.getPortalOpenedAt() == null) {
            instance.setPortalOpenedAt(LocalDateTime.now());
            // Implicit save omitted, typically done in service layer for this side-effect
        }
        return ResponseEntity.ok(instance.getTasks());
    }

    @PostMapping("/{token}/tasks/{taskId}/complete")
    public ResponseEntity<TaskInstance> completeTask(
            @PathVariable String token,
            @PathVariable UUID taskId) {
        OnboardingInstance instance = instanceService.getInstanceByToken(token);
        return ResponseEntity.ok(taskService.completeTask(taskId, instance.getId(), instance.getHireEmail()));
    }

    @PostMapping("/{token}/tasks/{taskId}/submit")
    public ResponseEntity<TaskInstance> submitTask(
            @PathVariable String token,
            @PathVariable UUID taskId,
            @RequestParam(required = false) String responseText,
            @RequestParam(required = false) String fileKey,
            @RequestParam(required = false) String fileName) {
        OnboardingInstance instance = instanceService.getInstanceByToken(token);
        return ResponseEntity.ok(taskService.submitTask(taskId, instance.getId(), instance.getHireEmail(), responseText, fileKey, fileName));
    }
}
