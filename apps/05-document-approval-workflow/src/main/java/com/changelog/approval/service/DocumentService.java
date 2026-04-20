package com.changelog.approval.service;

import com.changelog.approval.model.Document;
import com.changelog.approval.model.DocumentVersion;
import com.changelog.approval.repository.DocumentRepository;
import com.changelog.config.TenantResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;
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
            throw new RuntimeException("Access Denied");
        }
    }

    @Transactional
    public Document uploadDocument(String title, String description, String documentType, String department, String fileKey, String fileName, Long fileSizeBytes) {
        UUID tenantId = getCurrentTenantId();
        UUID userId = getCurrentUserId();

        Document document = Document.builder()
                .tenantId(tenantId)
                .title(title)
                .description(description)
                .documentType(documentType)
                .department(department)
                .originatedBy(userId)
                .currentVersion(1)
                .build();

        DocumentVersion version = DocumentVersion.builder()
                .document(document)
                .versionNumber(1)
                .fileKey(fileKey)
                .fileName(fileName)
                .fileSizeBytes(fileSizeBytes)
                .uploadedBy(userId)
                .build();

        document.setVersions(List.of(version));
        return documentRepository.save(document);
    }

    @Transactional
    public Document uploadVersion(UUID documentId, String fileKey, String fileName, Long fileSizeBytes) {
        Document document = getDocument(documentId);
        UUID userId = getCurrentUserId();
        
        int newVersionNumber = document.getCurrentVersion() + 1;
        document.setCurrentVersion(newVersionNumber);

        DocumentVersion version = DocumentVersion.builder()
                .document(document)
                .versionNumber(newVersionNumber)
                .fileKey(fileKey)
                .fileName(fileName)
                .fileSizeBytes(fileSizeBytes)
                .uploadedBy(userId)
                .build();

        document.getVersions().add(version);
        return documentRepository.save(document);
    }

    @Transactional(readOnly = true)
    public Document getDocument(UUID documentId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Document not found"));
        verifyTenant(document.getTenantId());
        return document;
    }

    @Transactional(readOnly = true)
    public List<Document> listDocuments() {
        return documentRepository.findByTenantId(getCurrentTenantId());
    }
}
