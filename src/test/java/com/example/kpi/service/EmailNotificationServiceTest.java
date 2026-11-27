package com.example.kpi.service;

import com.example.kpi.entity.Notification;
import com.example.kpi.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailNotificationServiceTest {

    @Mock
    private ObjectProvider<JavaMailSender> mailSenderProvider;
    @Mock
    private JavaMailSender mailSender;

    private EmailNotificationService service;

    @BeforeEach
    void setUp() {
        service = new EmailNotificationService(mailSenderProvider);
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "fromAddress", "no-reply@kpi.local");
        ReflectionTestUtils.setField(service, "subjectPrefix", "[KPI] ");
    }

    private Notification notification(String email) {
        User recipient = new User();
        recipient.setId(1L);
        recipient.setEmail(email);

        Notification n = new Notification();
        n.setRecipient(recipient);
        n.setTitle("KPI approved");
        n.setMessage("Your KPI was approved.");
        return n;
    }

    @Test
    void send_shouldDispatchWithPrefixedSubjectAndConfiguredSender() {
        when(mailSenderProvider.getIfAvailable()).thenReturn(mailSender);

        service.send("alice@example.invalid", "KPI approved", "Body text");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());

        SimpleMailMessage sent = captor.getValue();
        assertEquals("[KPI] KPI approved", sent.getSubject());
        assertEquals("no-reply@kpi.local", sent.getFrom());
        assertEquals("Body text", sent.getText());
    }

    @Test
    void send_shouldDoNothing_whenDisabled() {
        ReflectionTestUtils.setField(service, "enabled", false);

        service.send("alice@example.invalid", "Subject", "Body");

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void send_shouldDoNothing_whenNoMailSenderConfigured() {
        when(mailSenderProvider.getIfAvailable()).thenReturn(null);

        // Enabled but unconfigured must degrade quietly, not crash the caller.
        assertDoesNotThrow(() -> service.send("alice@example.invalid", "Subject", "Body"));
    }

    @Test
    void send_shouldSwallowTransportFailures() {
        when(mailSenderProvider.getIfAvailable()).thenReturn(mailSender);
        doThrow(new MailSendException("SMTP unreachable"))
                .when(mailSender).send(any(SimpleMailMessage.class));

        // A dead SMTP host must never roll back the notification transaction.
        assertDoesNotThrow(() -> service.send("alice@example.invalid", "Subject", "Body"));
    }

    @Test
    void sendAsync_shouldSkipRecipientWithoutEmail() {
        service.sendAsync(notification(null));
        service.sendAsync(notification("   "));

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void sendAsync_shouldTolerateNullNotification() {
        assertDoesNotThrow(() -> service.sendAsync(null));
    }

    @Test
    void isEnabled_shouldRequireBothFlagAndSender() {
        when(mailSenderProvider.getIfAvailable()).thenReturn(null);
        assertFalse(service.isEnabled(), "no sender means disabled even when the flag is on");

        when(mailSenderProvider.getIfAvailable()).thenReturn(mailSender);
        assertTrue(service.isEnabled());
    }
}

