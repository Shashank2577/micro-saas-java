package com.changelog.service;

import com.changelog.model.Board;
import com.changelog.repository.BoardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BoardService {

    private final BoardRepository boardRepository;

    @Transactional(readOnly = true)
    public List<Board> getBoards(UUID tenantId) {
        return boardRepository.findByTenantId(tenantId);
    }

    @Transactional(readOnly = true)
    public Board getBoard(UUID boardId, UUID tenantId) {
        return boardRepository.findByIdAndTenantId(boardId, tenantId)
                .orElseThrow(() -> new RuntimeException("Board not found"));
    }

    @Transactional(readOnly = true)
    public Board getBoardBySlug(UUID tenantId, String slug) {
        return boardRepository.findByTenantIdAndSlug(tenantId, slug)
                .orElseThrow(() -> new RuntimeException("Board not found"));
    }

    @Transactional
    public Board createBoard(Board board) {
        return boardRepository.save(board);
    }

    @Transactional
    public Board updateBoard(UUID boardId, UUID tenantId, Board updatedBoard) {
        Board board = getBoard(boardId, tenantId);
        board.setName(updatedBoard.getName());
        board.setDescription(updatedBoard.getDescription());
        board.setVisibility(updatedBoard.getVisibility());
        return boardRepository.save(board);
    }

    @Transactional
    public void deleteBoard(UUID boardId, UUID tenantId) {
        Board board = getBoard(boardId, tenantId);
        boardRepository.delete(board);
    }
}
