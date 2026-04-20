package com.changelog.business.retention.service;

import com.changelog.business.retention.model.DripCampaign;
import com.changelog.business.retention.model.DripEnrollment;
import com.changelog.business.retention.model.DripStep;
import com.changelog.business.retention.repository.DripCampaignRepository;
import com.changelog.business.retention.repository.DripEnrollmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class DripCampaignService {

    private final DripCampaignRepository campaignRepository;
    private final DripEnrollmentRepository enrollmentRepository;

    /**
     * Enroll a customer in a specific drip campaign.
     * Sets nextStepAt based on step[0].delayDays from now.
     */
    @Transactional
    public DripEnrollment enrollCustomer(UUID tenantId, UUID campaignId, UUID customerId, String email) {
        DripCampaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new IllegalArgumentException("Campaign not found: " + campaignId));

        // Skip if already enrolled
        if (enrollmentRepository.findByCustomerIdAndCampaignId(customerId, campaignId).isPresent()) {
            log.info("Customer {} already enrolled in campaign {}, skipping", customerId, campaignId);
            return enrollmentRepository.findByCustomerIdAndCampaignId(customerId, campaignId).get();
        }

        List<DripStep> steps = campaign.getSteps();
        LocalDateTime nextStepAt = steps.isEmpty()
                ? LocalDateTime.now()
                : LocalDateTime.now().plusDays(steps.get(0).getDelayDays());

        DripEnrollment enrollment = DripEnrollment.builder()
                .tenantId(tenantId)
                .campaignId(campaignId)
                .customerId(customerId)
                .customerEmail(email)
                .currentStep(0)
                .status("active")
                .nextStepAt(nextStepAt)
                .build();

        enrollment = enrollmentRepository.save(enrollment);
        log.info("Enrolled customer {} in campaign '{}' (tenantId={}), nextStepAt={}",
                customerId, campaign.getName(), tenantId, nextStepAt);
        return enrollment;
    }

    /**
     * Look up all active campaigns for a trigger event and enroll the customer in each.
     */
    @Transactional
    public void processEnrollmentTrigger(UUID tenantId, String triggerEvent, UUID customerId, String email) {
        List<DripCampaign> campaigns = campaignRepository.findByTenantIdAndTriggerEvent(tenantId, triggerEvent);

        List<DripCampaign> activeCampaigns = campaigns.stream()
                .filter(c -> "active".equals(c.getStatus()))
                .toList();

        if (activeCampaigns.isEmpty()) {
            log.debug("No active drip campaigns for tenant={} triggerEvent={}", tenantId, triggerEvent);
            return;
        }

        for (DripCampaign campaign : activeCampaigns) {
            enrollCustomer(tenantId, campaign.getId(), customerId, email);
        }
    }

    /**
     * Hourly scheduler that finds enrollments whose nextStepAt is in the past,
     * sends the email (logged), then advances to the next step or marks completed.
     */
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void processDueSteps() {
        List<DripEnrollment> dueEnrollments =
                enrollmentRepository.findByStatusAndNextStepAtBefore("active", LocalDateTime.now());

        log.info("processDueSteps: found {} due enrollments", dueEnrollments.size());

        for (DripEnrollment enrollment : dueEnrollments) {
            try {
                processSingleEnrollment(enrollment);
            } catch (Exception e) {
                log.error("Error processing enrollment {}: {}", enrollment.getId(), e.getMessage(), e);
            }
        }
    }

    private void processSingleEnrollment(DripEnrollment enrollment) {
        DripCampaign campaign = campaignRepository.findById(enrollment.getCampaignId())
                .orElse(null);

        if (campaign == null) {
            log.warn("Campaign {} not found for enrollment {}, skipping", enrollment.getCampaignId(), enrollment.getId());
            return;
        }

        List<DripStep> steps = campaign.getSteps();
        int currentStep = enrollment.getCurrentStep();

        if (currentStep >= steps.size()) {
            enrollment.setStatus("completed");
            enrollment.setCompletedAt(LocalDateTime.now());
            enrollmentRepository.save(enrollment);
            log.info("DRIP EMAIL: enrollment={} campaign='{}' customer={} — no more steps, marked completed",
                    enrollment.getId(), campaign.getName(), enrollment.getCustomerEmail());
            return;
        }

        DripStep step = steps.get(currentStep);

        // Send email (logged)
        log.info("DRIP EMAIL: to={} subject='{}' campaign='{}' step={} body='{}'",
                enrollment.getCustomerEmail(),
                step.getSubject(),
                campaign.getName(),
                step.getStepNumber(),
                step.getBodyTemplate());

        int nextStep = currentStep + 1;

        if (nextStep >= steps.size()) {
            // All steps done
            enrollment.setCurrentStep(nextStep);
            enrollment.setStatus("completed");
            enrollment.setCompletedAt(LocalDateTime.now());
            enrollment.setNextStepAt(null);
            log.info("DRIP EMAIL: enrollment={} completed all steps for customer={}",
                    enrollment.getId(), enrollment.getCustomerEmail());
        } else {
            // Advance to next step
            DripStep nextDripStep = steps.get(nextStep);
            enrollment.setCurrentStep(nextStep);
            enrollment.setNextStepAt(LocalDateTime.now().plusDays(nextDripStep.getDelayDays()));
        }

        enrollmentRepository.save(enrollment);
    }
}
