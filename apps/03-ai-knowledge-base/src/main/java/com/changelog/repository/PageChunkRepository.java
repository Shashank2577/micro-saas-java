package com.changelog.repository;

import com.changelog.model.PageChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PageChunkRepository extends JpaRepository<PageChunk, UUID> {
    List<PageChunk> findByPageIdOrderByChunkIndexAsc(UUID pageId);

    @Modifying
    @Query("DELETE FROM PageChunk p WHERE p.page.id = :pageId")
    void deleteByPageId(@Param("pageId") UUID pageId);

    @Query(value = "SELECT pc.id, pc.page_id, pc.chunk_index, pc.content, pc.embedding, pc.updated_at " +
                   "FROM page_chunks pc " +
                   "JOIN kb_pages kp ON pc.page_id = kp.id " +
                   "WHERE kp.tenant_id = :tenantId " +
                   "ORDER BY pc.embedding <=> CAST(:embedding AS vector) LIMIT :limit", nativeQuery = true)
    List<PageChunk> findSimilarChunks(@Param("tenantId") UUID tenantId, @Param("embedding") String embedding, @Param("limit") int limit);
}