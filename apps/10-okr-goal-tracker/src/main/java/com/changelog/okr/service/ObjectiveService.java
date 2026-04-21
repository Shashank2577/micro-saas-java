package com.changelog.okr.service;

import com.changelog.okr.model.KeyResult;
import com.changelog.okr.model.Objective;
import com.changelog.okr.repository.KeyResultRepository;
import com.changelog.okr.repository.ObjectiveRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.changelog.exception.EntityNotFoundException;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ObjectiveService {

    private final ObjectiveRepository objectiveRepository;
    private final KeyResultRepository keyResultRepository;

    @Transactional(readOnly = true)
    public List<Objective> listByCycle(UUID cycleId, UUID tenantId) {
        return objectiveRepository.findAllByTenantIdAndCycleId(tenantId, cycleId);
    }

    @Transactional(readOnly = true)
    public Objective getObjective(UUID objectiveId, UUID tenantId) {
        return objectiveRepository.findByIdAndTenantId(objectiveId, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Objective not found"));
    }

    @Transactional
    public Objective createObjective(UUID tenantId, UUID cycleId, Objective objective) {
        objective.setTenantId(tenantId);
        objective.setCycleId(cycleId);
        return objectiveRepository.save(objective);
    }

    @Transactional
    public Objective updateObjective(UUID objectiveId, UUID tenantId, Objective updates) {
        Objective existing = getObjective(objectiveId, tenantId);
        existing.setTitle(updates.getTitle());
        existing.setDescription(updates.getDescription());
        existing.setStatus(updates.getStatus());
        existing.setProgress(updates.getProgress());
        existing.setLevel(updates.getLevel());
        existing.setDepartment(updates.getDepartment());
        return objectiveRepository.save(existing);
    }

    @Transactional
    public void deleteObjective(UUID objectiveId, UUID tenantId) {
        Objective objective = getObjective(objectiveId, tenantId);
        objectiveRepository.delete(objective);
    }

    @Transactional(readOnly = true)
    public List<KeyResult> listKeyResults(UUID objectiveId) {
        return keyResultRepository.findAllByObjectiveId(objectiveId);
    }

    @Transactional
    public KeyResult createKeyResult(UUID objectiveId, UUID tenantId, KeyResult kr) {
        kr.setObjectiveId(objectiveId);
        kr.setTenantId(tenantId);
        return keyResultRepository.save(kr);
    }

    @Transactional
    public KeyResult updateKeyResult(UUID krId, UUID tenantId, KeyResult updates) {
        KeyResult existing = keyResultRepository.findById(krId)
                .orElseThrow(() -> new EntityNotFoundException("Key result not found"));

        if (!existing.getTenantId().equals(tenantId)) {
            throw new EntityNotFoundException("Key result not found");
        }

        existing.setTitle(updates.getTitle());
        existing.setCurrentValue(updates.getCurrentValue());
        existing.setConfidence(updates.getConfidence());
        existing.setCompleted(updates.isCompleted());
        if (existing.getTargetValue() != null && existing.getStartValue() != null
                && existing.getTargetValue().compareTo(existing.getStartValue()) != 0) {
            existing.setProgress(
                    existing.getCurrentValue().subtract(existing.getStartValue())
                            .divide(existing.getTargetValue().subtract(existing.getStartValue()), 4, java.math.RoundingMode.HALF_UP)
                            .multiply(java.math.BigDecimal.valueOf(100)));
        }
        return keyResultRepository.save(existing);
    }
}
