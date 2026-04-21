package com.changelog.okr.controller;

import com.changelog.config.TenantResolver;
import com.changelog.okr.model.KeyResult;
import com.changelog.okr.model.Objective;
import com.changelog.okr.model.OkrCycle;
import com.changelog.okr.service.ObjectiveService;
import com.changelog.okr.service.OkrCycleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class OkrController {

    private final OkrCycleService cycleService;
    private final ObjectiveService objectiveService;
    private final TenantResolver tenantResolver;

    // --- Cycles ---

    @GetMapping("/cycles")
    public ResponseEntity<List<OkrCycle>> listCycles(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(cycleService.listCycles(tenantResolver.getTenantId(jwt)));
    }

    @GetMapping("/cycles/{cycleId}")
    public ResponseEntity<OkrCycle> getCycle(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID cycleId) {
        return ResponseEntity.ok(cycleService.getCycle(cycleId, tenantResolver.getTenantId(jwt)));
    }

    @PostMapping("/cycles")
    public ResponseEntity<OkrCycle> createCycle(@AuthenticationPrincipal Jwt jwt, @RequestBody OkrCycle cycle) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(cycleService.createCycle(tenantResolver.getTenantId(jwt), cycle));
    }

    @PutMapping("/cycles/{cycleId}")
    public ResponseEntity<OkrCycle> updateCycle(@AuthenticationPrincipal Jwt jwt,
                                                @PathVariable UUID cycleId,
                                                @RequestBody OkrCycle updates) {
        return ResponseEntity.ok(cycleService.updateCycle(cycleId, tenantResolver.getTenantId(jwt), updates));
    }

    @DeleteMapping("/cycles/{cycleId}")
    public ResponseEntity<Void> deleteCycle(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID cycleId) {
        cycleService.deleteCycle(cycleId, tenantResolver.getTenantId(jwt));
        return ResponseEntity.noContent().build();
    }

    // --- Objectives ---

    @GetMapping("/cycles/{cycleId}/objectives")
    public ResponseEntity<List<Objective>> listObjectives(@AuthenticationPrincipal Jwt jwt,
                                                          @PathVariable UUID cycleId) {
        return ResponseEntity.ok(objectiveService.listByCycle(cycleId, tenantResolver.getTenantId(jwt)));
    }

    @PostMapping("/cycles/{cycleId}/objectives")
    public ResponseEntity<Objective> createObjective(@AuthenticationPrincipal Jwt jwt,
                                                     @PathVariable UUID cycleId,
                                                     @RequestBody Objective objective) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(objectiveService.createObjective(tenantResolver.getTenantId(jwt), cycleId, objective));
    }

    @PutMapping("/objectives/{objectiveId}")
    public ResponseEntity<Objective> updateObjective(@AuthenticationPrincipal Jwt jwt,
                                                     @PathVariable UUID objectiveId,
                                                     @RequestBody Objective updates) {
        return ResponseEntity.ok(objectiveService.updateObjective(objectiveId, tenantResolver.getTenantId(jwt), updates));
    }

    @DeleteMapping("/objectives/{objectiveId}")
    public ResponseEntity<Void> deleteObjective(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID objectiveId) {
        objectiveService.deleteObjective(objectiveId, tenantResolver.getTenantId(jwt));
        return ResponseEntity.noContent().build();
    }

    // --- Key Results ---

    @GetMapping("/objectives/{objectiveId}/key-results")
    public ResponseEntity<List<KeyResult>> listKeyResults(@PathVariable UUID objectiveId) {
        return ResponseEntity.ok(objectiveService.listKeyResults(objectiveId));
    }

    @PostMapping("/objectives/{objectiveId}/key-results")
    public ResponseEntity<KeyResult> createKeyResult(@AuthenticationPrincipal Jwt jwt,
                                                     @PathVariable UUID objectiveId,
                                                     @RequestBody KeyResult kr) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(objectiveService.createKeyResult(objectiveId, tenantResolver.getTenantId(jwt), kr));
    }

    @PutMapping("/key-results/{krId}")
    public ResponseEntity<KeyResult> updateKeyResult(@PathVariable UUID krId, @RequestBody KeyResult updates) {
        return ResponseEntity.ok(objectiveService.updateKeyResult(krId, updates));
    }
}
