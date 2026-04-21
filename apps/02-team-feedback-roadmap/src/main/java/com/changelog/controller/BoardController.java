package com.changelog.controller;

import com.changelog.config.TenantResolver;
import com.changelog.model.Board;
import com.changelog.service.BoardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/boards")
@RequiredArgsConstructor
public class BoardController {

    private final BoardService boardService;
    private final TenantResolver tenantResolver;

    @GetMapping
    public ResponseEntity<List<Board>> getBoards(@AuthenticationPrincipal Jwt jwt) {
        UUID tenantId = tenantResolver.getTenantId(jwt);
        return ResponseEntity.ok(boardService.getBoards(tenantId));
    }

    @PostMapping
    public ResponseEntity<Board> createBoard(@AuthenticationPrincipal Jwt jwt, @RequestBody Board board) {
        UUID tenantId = tenantResolver.getTenantId(jwt);
        board.setTenantId(tenantId);
        return ResponseEntity.ok(boardService.createBoard(board));
    }

    @GetMapping("/{boardId}")
    public ResponseEntity<Board> getBoard(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID boardId) {
        UUID tenantId = tenantResolver.getTenantId(jwt);
        return ResponseEntity.ok(boardService.getBoard(boardId, tenantId));
    }

    @PutMapping("/{boardId}")
    public ResponseEntity<Board> updateBoard(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID boardId, @RequestBody Board board) {
        UUID tenantId = tenantResolver.getTenantId(jwt);
        return ResponseEntity.ok(boardService.updateBoard(boardId, tenantId, board));
    }

    @DeleteMapping("/{boardId}")
    public ResponseEntity<Void> deleteBoard(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID boardId) {
        UUID tenantId = tenantResolver.getTenantId(jwt);
        boardService.deleteBoard(boardId, tenantId);
        return ResponseEntity.noContent().build();
    }
}
