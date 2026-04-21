package com.changelog.business.portals.service;

import com.changelog.business.portals.model.Deliverable;
import com.changelog.business.portals.model.DeliverableVersion;
import com.changelog.business.portals.model.Approval;
import com.changelog.business.portals.repository.DeliverableRepository;
import com.changelog.business.portals.repository.DeliverableVersionRepository;
import com.changelog.business.portals.repository.ApprovalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeliverableService {

    private final DeliverableRepository deliverableRepository;
    private final DeliverableVersionRepository versionRepository;
    private final ApprovalRepository approvalRepository;

    public List<Deliverable> getDeliverablesBySection(UUID sectionId) {
        return deliverableRepository.findBySectionId(sectionId);
    }

    public Deliverable createDeliverable(Deliverable deliverable) {
        return deliverableRepository.save(deliverable);
    }

    public Optional<Deliverable> getDeliverable(UUID id) {
        return deliverableRepository.findById(id);
    }

    public DeliverableVersion addVersion(DeliverableVersion version) {
        return versionRepository.save(version);
    }

    public Approval addApproval(Approval approval) {
        return approvalRepository.save(approval);
    }
}
