package com.changelog.service;

import com.changelog.model.Post;
import com.changelog.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class ScheduledPublishingJob {

    private final PostRepository postRepository;
    private final SubscriberNotificationService subscriberNotificationService;

    @Scheduled(cron = "0 * * * * *")
    @Transactional
    public void publishScheduledPosts() {
        List<Post> due = postRepository.findScheduledPostsDueForPublishing(LocalDateTime.now());

        if (due.isEmpty()) {
            return;
        }

        log.info("ScheduledPublishingJob: found {} posts due for publishing", due.size());

        for (Post post : due) {
            try {
                post.setStatus(Post.PostStatus.PUBLISHED);
                post.setPublishedAt(LocalDateTime.now());
                Post saved = postRepository.save(post);
                log.info("Published scheduled post: id={} title='{}' project={}", saved.getId(), saved.getTitle(), saved.getProjectId());

                try {
                    subscriberNotificationService.notifySubscribers(saved);
                } catch (Exception e) {
                    log.error("Notification failed for post {}: {}", saved.getId(), e.getMessage());
                }
            } catch (Exception e) {
                log.error("Failed to publish scheduled post {}: {}", post.getId(), e.getMessage());
            }
        }
    }
}
