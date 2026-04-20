package com.changelog.repository;

import com.changelog.model.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PostRepository extends JpaRepository<Post, UUID> {

    @EntityGraph(attributePaths = {"tags"})
    List<Post> findByProjectIdOrderByPublishedAtDesc(UUID projectId);

    @EntityGraph(attributePaths = {"tags"})
    List<Post> findByProjectIdAndStatusOrderByPublishedAtDesc(UUID projectId, Post.PostStatus status);

    @EntityGraph(attributePaths = {"tags"})
    Page<Post> findByProjectIdAndStatus(UUID projectId, Post.PostStatus status, Pageable pageable);

    @Query("SELECT p FROM Post p WHERE p.projectId = :projectId AND p.status = 'PUBLISHED' ORDER BY p.publishedAt DESC")
    @EntityGraph(attributePaths = {"tags"})
    List<Post> findPublishedPosts(@Param("projectId") UUID projectId);

    @Query("SELECT p FROM Post p WHERE p.status = 'SCHEDULED' AND p.scheduledFor <= :now")
    List<Post> findScheduledPostsDueForPublishing(@Param("now") LocalDateTime now);

    @Query("SELECT p FROM Post p JOIN p.tags t WHERE p.projectId = :projectId AND p.status = 'PUBLISHED' AND t.name = :tagName ORDER BY p.publishedAt DESC")
    @EntityGraph(attributePaths = {"tags"})
    List<Post> findPublishedPostsByTag(@Param("projectId") UUID projectId, @Param("tagName") String tagName);

    @Query("SELECT p FROM Post p LEFT JOIN FETCH p.tags WHERE p.tenantId = :tenantId AND p.id = :postId")
    Optional<Post> findByIdWithTags(@Param("tenantId") UUID tenantId, @Param("postId") UUID postId);

    long countByTenantId(UUID tenantId);

    long countByTenantIdAndStatus(UUID tenantId, Post.PostStatus status);

    @Query("SELECT p FROM Post p WHERE p.tenantId = :tenantId AND p.title ILIKE CONCAT('%', :query, '%') AND p.status = 'PUBLISHED'")
    List<Post> searchPublishedPosts(@Param("tenantId") UUID tenantId, @Param("query") String query);

    @Query(value = """
        SELECT p.*, ts_rank(p.content_tsv, plainto_tsquery('english', :query)) AS rank
        FROM changelog_posts p
        WHERE p.project_id = :projectId
          AND p.status = 'PUBLISHED'
          AND p.content_tsv @@ plainto_tsquery('english', :query)
        ORDER BY rank DESC
        """, nativeQuery = true)
    List<Object[]> searchPublishedPostsByProject(@Param("projectId") UUID projectId, @Param("query") String query);
}
