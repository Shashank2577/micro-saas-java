package com.changelog.okr.service;

import com.changelog.okr.model.OkrCycle;
import com.changelog.okr.repository.OkrCycleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OkrCycleService {

    private final OkrCycleRepository cycleRepository;

    @Transactional(readOnly = true)
    public List<OkrCycle> listCycles(UUID tenantId) {
        return cycleRepository.findByTenantIdOrderByStartDateDesc(tenantId);
    }

    @Transactional(readOnly = true)
    public OkrCycle getCycle(UUID cycleId, UUID tenantId) {
        return cycleRepository.findByIdAndTenantId(cycleId, tenantId)
                .orElseThrow(() -> new RuntimeException("OKR cycle not found"));
    }

    @Transactional
    public OkrCycle createCycle(UUID tenantId, OkrCycle cycle) {
        cycle.setTenantId(tenantId);
        return cycleRepository.save(cycle);
    }

    @Transactional
    public OkrCycle updateCycle(UUID cycleId, UUID tenantId, OkrCycle updates) {
        OkrCycle existing = getCycle(cycleId, tenantId);
        existing.setName(updates.getName());
        existing.setStartDate(updates.getStartDate());
        existing.setEndDate(updates.getEndDate());
        existing.setStatus(updates.getStatus());
        return cycleRepository.save(existing);
    }

    @Transactional
    public void deleteCycle(UUID cycleId, UUID tenantId) {
        OkrCycle cycle = getCycle(cycleId, tenantId);
        cycleRepository.delete(cycle);
    }
}
