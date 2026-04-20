package com.changelog.controller;

import com.changelog.dto.ProjectResponse;
import com.changelog.model.Post;
import com.changelog.repository.PostRepository;
import com.changelog.service.ProjectService;
import com.rometools.rome.feed.synd.*;
import com.rometools.rome.io.FeedException;
import com.rometools.rome.io.SyndFeedOutput;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@RestController
@RequestMapping("/changelog")
@RequiredArgsConstructor
public class RssFeedController {

    private final ProjectService projectService;
    private final PostRepository postRepository;

    @Value("${app.public-url:http://localhost:8080}")
    private String publicUrl;

    @GetMapping(value = "/{slug}/feed.xml", produces = "application/rss+xml;charset=UTF-8")
    public ResponseEntity<String> getRssFeed(@PathVariable String slug) {
        ProjectResponse project;
        try {
            project = projectService.getProjectBySlug(slug);
        } catch (EntityNotFoundException e) {
            return ResponseEntity.notFound().build();
        }

        List<Post> posts = postRepository.findByProjectIdAndStatusOrderByPublishedAtDesc(
                project.getId(), Post.PostStatus.PUBLISHED);

        SyndFeed feed = new SyndFeedImpl();
        feed.setFeedType("rss_2.0");
        feed.setTitle(project.getName() + " Changelog");
        feed.setDescription(project.getDescription() != null ? project.getDescription() : project.getName() + " release notes");
        feed.setLink(publicUrl + "/changelog/" + slug);

        List<SyndEntry> entries = new ArrayList<>();
        for (Post post : posts) {
            SyndEntry entry = new SyndEntryImpl();
            entry.setTitle(post.getTitle());
            entry.setLink(publicUrl + "/changelog/" + slug + "/" + post.getId());
            entry.setUri(post.getId().toString());

            if (post.getPublishedAt() != null) {
                // Use system default zone since publishedAt is stored as LocalDateTime in server timezone
                entry.setPublishedDate(Date.from(post.getPublishedAt().atZone(ZoneId.systemDefault()).toInstant()));
            }

            SyndContent description = new SyndContentImpl();
            description.setType("text/plain");
            String summary = post.getSummary() != null ? post.getSummary() : truncate(post.getContent(), 200);
            description.setValue(summary);
            entry.setDescription(description);

            // Use full permalink URL as GUID so RSS readers deduplicate correctly
            entry.setUri(publicUrl + "/changelog/" + slug + "/" + post.getId());

            entries.add(entry);
        }
        feed.setEntries(entries);

        try {
            String xml = new SyndFeedOutput().outputString(feed);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("application/rss+xml;charset=UTF-8"))
                    .body(xml);
        } catch (FeedException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
    }
}
