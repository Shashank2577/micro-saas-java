package com.changelog.approval.controller;

import com.changelog.approval.model.WorkflowTemplate;
import com.changelog.approval.service.WorkflowTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/templates")
@RequiredArgsConstructor
public class TemplateController {

    private final WorkflowTemplateService templateService;

    @GetMapping
    public ResponseEntity<List<WorkflowTemplate>> listTemplates() {
        return ResponseEntity.ok(templateService.listTemplates());
    }

    @PostMapping
    public ResponseEntity<WorkflowTemplate> createTemplate(@RequestBody WorkflowTemplate template) {
        return ResponseEntity.status(HttpStatus.CREATED).body(templateService.createTemplate(template));
    }

    @GetMapping("/{templateId}")
    public ResponseEntity<WorkflowTemplate> getTemplate(@PathVariable UUID templateId) {
        return ResponseEntity.ok(templateService.getTemplate(templateId));
    }

    @PutMapping("/{templateId}")
    public ResponseEntity<WorkflowTemplate> updateTemplate(
            @PathVariable UUID templateId,
            @RequestBody WorkflowTemplate template) {
        return ResponseEntity.ok(templateService.updateTemplate(templateId, template));
    }

    @DeleteMapping("/{templateId}")
    public ResponseEntity<Void> archiveTemplate(@PathVariable UUID templateId) {
        templateService.archiveTemplate(templateId);
        return ResponseEntity.noContent().build();
    }
}
