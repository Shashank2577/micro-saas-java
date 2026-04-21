package com.changelog.okr.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "check_in_reminders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CheckInReminder {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "week_start", nullable = false)
    private LocalDate weekStart;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "sent_at")
    private OffsetDateTime sentAt;

    @Column(name = "reminder_sent_at")
    private OffsetDateTime reminderSentAt;

    @Column(name = "checked_in_at")
    private OffsetDateTime checkedInAt;
}
