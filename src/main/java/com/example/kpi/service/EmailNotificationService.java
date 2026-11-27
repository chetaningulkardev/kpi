package com.example.kpi.service;

import com.example.kpi.entity.Notification;
import com.example.kpi.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Sends notification emails.
 *
 * <p>Mail is treated as strictly best-effort: the {@link JavaMailSender} is
 * resolved lazily through an {@link ObjectProvider} so the application still
 * starts (and the in-app notification still lands) when no SMTP server is
 * configured, which is the normal case for local development and tests.</p>
 */
@Service
@Slf4j
public class EmailNotificationService {

    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    @Value("${app.mail.enabled:false}")
    private boolean enabled;

    @Value("${app.mail.from:no-reply@kpi.local}")
    private String fromAddress;

    @Value("${app.mail.subject-prefix:[KPI] }")
    private String subjectPrefix;

    public EmailNotificationService(ObjectProvider<JavaMailSender> mailSenderProvider) {
        this.mailSenderProvider = mailSenderProvider;
    }

    /** Whether email delivery is switched on and a sender is actually available. */
    public boolean isEnabled() {
        return enabled && mailSenderProvider.getIfAvailable() != null;
    }

    /**
     * Deliver a notification by email on a background thread.
     *
     * <p>Never throws: a bad address or an unreachable SMTP host must not roll
     * back the transaction that produced the notification.</p>
     */
    @Async("notificationExecutor")
    public void sendAsync(Notification notification) {
        if (notification == null) {
            return;
        }
        User recipient = notification.getRecipient();
        if (recipient == null || recipient.getEmail() == null || recipient.getEmail().isBlank()) {
            return;
        }
        send(recipient.getEmail(), notification.getTitle(), notification.getMessage());
    }

    /** Deliver a plain-text message. Failures are logged, never propagated. */
    public void send(String to, String subject, String body) {
        if (!enabled) {
            log.debug("Email disabled; skipping message to {}", to);
            return;
        }
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null) {
            log.warn("app.mail.enabled=true but no JavaMailSender is configured; skipping email to {}", to);
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(to);
            message.setSubject(subjectPrefix + (subject == null ? "Notification" : subject));
            message.setText(body == null ? "" : body);
            sender.send(message);
            log.debug("Notification email sent to {}", to);
        } catch (RuntimeException ex) {
            // MailException is a RuntimeException; catching broadly guarantees a
            // notification is never lost to a transport-layer problem.
            log.warn("Failed to send notification email to {}: {}", to, ex.getMessage());
        }
    }
}

