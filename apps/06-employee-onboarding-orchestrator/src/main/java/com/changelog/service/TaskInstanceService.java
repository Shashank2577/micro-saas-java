package com.changelog.service;

import com.changelog.model.OnboardingInstance;
import com.changelog.model.TaskInstance;
import com.changelog.model.TaskSubmission;
import com.changelog.repository.OnboardingInstanceRepository;
import com.changelog.repository.TaskInstanceRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TaskInstanceService {

    private final TaskInstanceRepository taskRepository;
    private final OnboardingInstanceRepository instanceRepository;

    @Transactional(readOnly = true)
    public TaskInstance getTask(UUID taskId, UUID instanceId) {
        return taskRepository.findByIdAndOnboardingInstanceId(taskId, instanceId)
                .orElseThrow(() -> new EntityNotFoundException("Task not found"));
    }

    @Transactional
    public TaskInstance completeTask(UUID taskId, UUID instanceId, String completedBy) {
        TaskInstance task = getTask(taskId, instanceId);
        task.setStatus("completed");
        task.setCompletedAt(LocalDateTime.now());
        task.setCompletedBy(completedBy);

        TaskInstance saved = taskRepository.save(task);
        checkAllTasksCompleted(task.getOnboardingInstance().getId());
        return saved;
    }

    @Transactional
    public TaskInstance skipTask(UUID taskId, UUID instanceId, String completedBy) {
        TaskInstance task = getTask(taskId, instanceId);
        task.setStatus("skipped");
        task.setCompletedAt(LocalDateTime.now());
        task.setCompletedBy(completedBy);

        TaskInstance saved = taskRepository.save(task);
        checkAllTasksCompleted(task.getOnboardingInstance().getId());
        return saved;
    }

    @Transactional
    public TaskInstance submitTask(UUID taskId, UUID instanceId, String completedBy, String responseText, String fileKey, String fileName) {
        TaskInstance task = getTask(taskId, instanceId);

        TaskSubmission submission = TaskSubmission.builder()
                .taskInstance(task)
                .responseText(responseText)
                .fileKey(fileKey)
                .fileName(fileName)
                .build();

        task.setSubmission(submission);
        task.setStatus("completed");
        task.setCompletedAt(LocalDateTime.now());
        task.setCompletedBy(completedBy);

        TaskInstance saved = taskRepository.save(task);
        checkAllTasksCompleted(task.getOnboardingInstance().getId());
        return saved;
    }

    private void checkAllTasksCompleted(UUID instanceId) {
        OnboardingInstance instance = instanceRepository.findById(instanceId)
                .orElseThrow(() -> new EntityNotFoundException("Instance not found"));

        boolean allDone = instance.getTasks().stream()
                .allMatch(t -> "completed".equals(t.getStatus()) || "skipped".equals(t.getStatus()));

        if (allDone && !"completed".equals(instance.getStatus())) {
            instance.setStatus("completed");
            instance.setCompletedAt(LocalDateTime.now());
            instanceRepository.save(instance);
        }
    }
}
