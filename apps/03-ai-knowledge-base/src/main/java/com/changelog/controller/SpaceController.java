package com.changelog.controller;

import com.changelog.model.Space;
import com.changelog.service.SpaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/spaces")
@RequiredArgsConstructor
public class SpaceController {

    private final SpaceService spaceService;

    @GetMapping
    public ResponseEntity<List<Space>> getSpaces(@RequestHeader("X-Tenant-ID") UUID tenantId) {
        return ResponseEntity.ok(spaceService.getSpaces(tenantId));
    }

    @PostMapping
    public ResponseEntity<Space> createSpace(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @RequestBody Space space) {
        return ResponseEntity.ok(spaceService.createSpace(tenantId, space));
    }

    @GetMapping("/{spaceId}")
    public ResponseEntity<Space> getSpace(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @PathVariable UUID spaceId) {
        return spaceService.getSpace(tenantId, spaceId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}