package com.changelog.repository;

import com.changelog.model.Post;
import com.changelog.model.Project;
import com.changelog.model.Tenant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase(replace = org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("local")
class PostRepositoryIntegrationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private PostRepository postRepository;

    @Test
    void shouldOnlyReturnPostsForSpecificTenant() {
        String uniqueSuffix = UUID.randomUUID().toString();
        
        // Setup Tenant 1
        Tenant tenant1 = new Tenant();
        tenant1.setName("Tenant 1");
        tenant1.setSlug("tenant-1-" + uniqueSuffix);
        tenant1 = entityManager.persistAndFlush(tenant1);

        Project project1 = Project.builder()
                .tenantId(tenant1.getId())
                .name("Project 1")
                .slug("project-1-" + uniqueSuffix)
                .branding(Map.of())
                .build();
        project1 = entityManager.persistAndFlush(project1);

        Post post1 = Post.builder()
                .projectId(project1.getId())
                .tenantId(tenant1.getId())
                .title("Post 1")
                .content("Content 1")
                .status(Post.PostStatus.PUBLISHED)
                .build();
        entityManager.persist(post1);

        // Setup Tenant 2
        String uniqueSuffix2 = UUID.randomUUID().toString();
        Tenant tenant2 = new Tenant();
        tenant2.setName("Tenant 2");
        tenant2.setSlug("tenant-2-" + uniqueSuffix2);
        tenant2 = entityManager.persistAndFlush(tenant2);

        Project project2 = Project.builder()
                .tenantId(tenant2.getId())
                .name("Project 2")
                .slug("project-2-" + uniqueSuffix2)
                .branding(Map.of())
                .build();
        project2 = entityManager.persistAndFlush(project2);

        Post post2 = Post.builder()
                .projectId(project2.getId())
                .tenantId(tenant2.getId())
                .title("Post 2")
                .content("Content 2")
                .status(Post.PostStatus.PUBLISHED)
                .build();
        entityManager.persist(post2);

        entityManager.flush();

        // Test Isolation
        List<Post> tenant1Posts = postRepository.findByProjectIdOrderByPublishedAtDesc(project1.getId());
        assertEquals(1, tenant1Posts.size());
        assertEquals("Post 1", tenant1Posts.get(0).getTitle());
        assertEquals(tenant1.getId(), tenant1Posts.get(0).getTenantId());
    }
}
