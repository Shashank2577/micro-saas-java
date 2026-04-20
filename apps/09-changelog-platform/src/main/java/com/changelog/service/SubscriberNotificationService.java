package com.changelog.service;

import com.changelog.model.Post;
import com.changelog.model.Project;
import com.changelog.model.Subscriber;
import com.changelog.repository.ProjectRepository;
import com.changelog.repository.SubscriberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class SubscriberNotificationService {

    private final SubscriberRepository subscriberRepository;
    private final ProjectRepository projectRepository;
    private final JavaMailSender mailSender;

    @Value("${app.public-url:http://localhost:8080}")
    private String publicUrl;

    @Value("${app.notification-email:noreply@changelog-platform.io}")
    private String fromEmail;

    @Async
    public void notifySubscribers(Post post) {
        Project project = projectRepository.findById(post.getProjectId()).orElse(null);
        if (project == null) {
            log.warn("Project {} not found for post {}, skipping notifications", post.getProjectId(), post.getId());
            return;
        }

        List<Subscriber> activeSubscribers = subscriberRepository.findByProjectId(post.getProjectId())
                .stream()
                .filter(s -> s.getStatus() == Subscriber.SubscriberStatus.ACTIVE)
                .toList();

        if (activeSubscribers.isEmpty()) {
            log.debug("No active subscribers for project {}", project.getSlug());
            return;
        }

        // Sanitize subject to prevent CRLF header injection
        String subject = "[" + sanitize(project.getName()) + "] " + sanitize(post.getTitle());
        String postUrl = publicUrl + "/changelog/" + project.getSlug() + "/" + post.getId();
        String body = buildEmailBody(post, project, postUrl);

        int sent = 0;
        for (Subscriber subscriber : activeSubscribers) {
            try {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setFrom(fromEmail);
                message.setTo(subscriber.getEmail());
                message.setSubject(subject);
                message.setText(body);
                mailSender.send(message);
                sent++;
            } catch (Exception e) {
                log.error("Failed to send notification to {}: {}", subscriber.getEmail(), e.getMessage());
            }
        }

        log.info("Sent notifications to {}/{} subscribers for post '{}'", sent, activeSubscribers.size(), post.getTitle());
    }

    /** Strip CRLF characters to prevent email header injection. */
    private String sanitize(String value) {
        if (value == null) return "";
        return value.replaceAll("[\r\n]", " ").trim();
    }

    private String buildEmailBody(Post post, Project project, String postUrl) {
        StringBuilder body = new StringBuilder();
        body.append(post.getTitle()).append("\n\n");
        if (post.getSummary() != null && !post.getSummary().isBlank()) {
            body.append(post.getSummary()).append("\n\n");
        }
        body.append("Read the full update: ").append(postUrl).append("\n\n");
        body.append("---\n");
        body.append("You're receiving this because you subscribed to ").append(project.getName()).append(" updates.\n");
        body.append("Unsubscribe: ").append(publicUrl).append("/changelog/").append(project.getSlug()).append("/unsubscribe");
        return body.toString();
    }
}
