package com.changelog.issuetracker.service;

import com.changelog.issuetracker.model.Label;
import com.changelog.issuetracker.repository.LabelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LabelService {
    private final LabelRepository labelRepository;

    public List<Label> getAllLabels(UUID tenantId) {
        return labelRepository.findAllByTenantId(tenantId);
    }

    @Transactional
    public Label createLabel(Label label) {
        if (label.getId() == null) {
            label.setId(UUID.randomUUID());
        }
        return labelRepository.save(label);
    }
}
