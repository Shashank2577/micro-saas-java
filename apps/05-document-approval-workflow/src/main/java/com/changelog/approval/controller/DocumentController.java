package com.changelog.approval.controller;

import com.changelog.approval.model.Document;
import com.changelog.approval.service.DocumentService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    @GetMapping
    public ResponseEntity<List<Document>> listDocuments() {
        return ResponseEntity.ok(documentService.listDocuments());
    }

    @PostMapping
    public ResponseEntity<Document> uploadDocument(@RequestBody DocumentUploadRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(documentService.uploadDocument(
                        request.getTitle(),
                        request.getDescription(),
                        request.getDocumentType(),
                        request.getDepartment(),
                        request.getFileKey(),
                        request.getFileName(),
                        request.getFileSizeBytes()
                ));
    }

    @GetMapping("/{docId}")
    public ResponseEntity<Document> getDocument(@PathVariable UUID docId) {
        return ResponseEntity.ok(documentService.getDocument(docId));
    }

    @PostMapping("/{docId}/versions")
    public ResponseEntity<Document> uploadVersion(
            @PathVariable UUID docId,
            @RequestBody VersionUploadRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(documentService.uploadVersion(
                        docId,
                        request.getFileKey(),
                        request.getFileName(),
                        request.getFileSizeBytes()
                ));
    }

    @Data
    public static class DocumentUploadRequest {
        private String title;
        private String description;
        private String documentType;
        private String department;
        private String fileKey;
        private String fileName;
        private Long fileSizeBytes;
    }

    @Data
    public static class VersionUploadRequest {
        private String fileKey;
        private String fileName;
        private Long fileSizeBytes;
    }
}
