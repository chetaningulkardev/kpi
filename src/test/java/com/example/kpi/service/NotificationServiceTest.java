package com.example.kpi.service;

import com.example.kpi.entity.Notification;
import com.example.kpi.entity.User;
import com.example.kpi.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private EmailNotificationService emailService;

    @InjectMocks
    private NotificationService service;

    @Test
    void create_shouldReturnNull_whenRecipientMissing() {
        Notification created = service.create(null, "title", "message", "INFO", "KPI", 1L);

        assertNull(created);
        verifyNoInteractions(notificationRepository);
        verifyNoInteractions(emailService);
    }

    @Test
    void create_shouldMirrorNotificationToEmail() {
        User recipient = new User();
        recipient.setId(5L);
        recipient.setEmail("alice@example.invalid");

        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Notification created = service.create(recipient, "KPI approved", "Nice work",
                "KPI_REVIEWED", "KPI", 42L);

        assertNotNull(created);
        // The saved entity is what gets emailed, so the mail carries the final
        // normalised title/type rather than the raw arguments.
        verify(emailService).sendAsync(created);
    }

    @Test
    void markRead_shouldSetReadAndTimestamp_forOwner() {
        User recipient = new User();
        recipient.setId(11L);

        Notification notification = new Notification();
        notification.setId(77L);
        notification.setRecipient(recipient);
        notification.setRead(false);

        when(notificationRepository.findById(77L)).thenReturn(Optional.of(notification));

        service.markRead(77L, 11L);

        assertTrue(notification.getRead());
        assertNotNull(notification.getReadAt());
        verify(notificationRepository).save(notification);
    }

    @Test
    void markRead_shouldThrow_forDifferentUser() {
        User recipient = new User();
        recipient.setId(11L);

        Notification notification = new Notification();
        notification.setId(77L);
        notification.setRecipient(recipient);

        when(notificationRepository.findById(77L)).thenReturn(Optional.of(notification));

        assertThrows(SecurityException.class, () -> service.markRead(77L, 99L));
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void markAllRead_shouldOnlyUpdateUnreadItems() {
        User recipient = new User();
        recipient.setId(11L);

        Notification n1 = new Notification();
        n1.setId(1L);
        n1.setRecipient(recipient);
        n1.setRead(false);

        Notification n2 = new Notification();
        n2.setId(2L);
        n2.setRecipient(recipient);
        n2.setRead(true);

        Notification n3 = new Notification();
        n3.setId(3L);
        n3.setRecipient(recipient);
        n3.setRead(false);

        when(notificationRepository.findTop50ByRecipientIdOrderByCreatedDateDesc(11L)).thenReturn(List.of(n1, n2, n3));

        int changed = service.markAllRead(11L);

        assertEquals(2, changed);
        assertTrue(n1.getRead());
        assertTrue(n2.getRead());
        assertTrue(n3.getRead());
        assertNotNull(n1.getReadAt());
        assertNotNull(n3.getReadAt());
        verify(notificationRepository).saveAll(anyList());
    }

    @Test
    void searchForUser_shouldApplySafePagingBounds() {
        when(notificationRepository.searchByRecipient(anyLong(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.searchForUser(11L, null, null, null, null, -4, 999, "newest");

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(notificationRepository).searchByRecipient(eq(11L), isNull(), isNull(), isNull(), isNull(), pageableCaptor.capture());
        Pageable pageable = pageableCaptor.getValue();

        assertEquals(0, pageable.getPageNumber());
        assertEquals(100, pageable.getPageSize());
    }

    @Test
    void getAvailableTypes_shouldReturnDistinctSortedTypesFromRepository() {
        when(notificationRepository.findDistinctTypesByRecipientId(11L)).thenReturn(List.of("INFO", "KPI_REVIEWED"));

        List<String> types = service.getAvailableTypes(11L);

        assertEquals(2, types.size());
        assertEquals("INFO", types.get(0));
        assertEquals("KPI_REVIEWED", types.get(1));
    }
}

