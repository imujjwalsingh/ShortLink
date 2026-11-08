package com.shortlink.notificationservice.service;

import com.shortlink.notificationservice.event.LinkMilestoneEvent;
import com.shortlink.notificationservice.event.UserRegisteredEvent;
import com.shortlink.notificationservice.model.Contact;
import com.shortlink.notificationservice.model.SentNotification;
import com.shortlink.notificationservice.repository.ContactRepository;
import com.shortlink.notificationservice.repository.SentNotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private ContactRepository contactRepository;

    @Mock
    private SentNotificationRepository sentRepository;

    @Mock
    private EmailSender emailSender;

    private NotificationService service;

    @BeforeEach
    void setUp() {
        service = new NotificationService(contactRepository, sentRepository, emailSender, "http://sho.rt");
    }

    private static UserRegisteredEvent registered() {
        return new UserRegisteredEvent("user-registered:u1", "u1", "alice@example.com", Instant.now());
    }

    private static LinkMilestoneEvent milestone(long n) {
        return new LinkMilestoneEvent("milestone:abc:" + n, "abc", "u1", n, Instant.now());
    }

    @Test
    void registrationStoresContactSendsWelcomeAndRecordsIt() {
        when(sentRepository.existsById("user-registered:u1")).thenReturn(false);

        service.handleUserRegistered(registered());

        verify(contactRepository).save(argThat(c -> c.userId().equals("u1") && c.email().equals("alice@example.com")));
        verify(emailSender).send(eq("alice@example.com"), eq("Welcome to ShortLink"), contains("http://sho.rt/api/urls"));
        verify(sentRepository).save(argThat((SentNotification s) -> s.eventId().equals("user-registered:u1")
                && s.type().equals("WELCOME")));
    }

    @Test
    void redeliveredEventDoesNotSendASecondEmail() {
        when(sentRepository.existsById("user-registered:u1")).thenReturn(true);

        service.handleUserRegistered(registered());

        verifyNoInteractions(emailSender);
        verify(sentRepository, never()).save(any());
    }

    @Test
    void smtpFailurePropagatesForRetryAndRecordsNothing() {
        when(sentRepository.existsById(anyString())).thenReturn(false);
        doThrow(new MailSendException("connection refused")).when(emailSender).send(anyString(), anyString(), anyString());

        assertThatThrownBy(() -> service.handleUserRegistered(registered()))
                .isInstanceOf(MailSendException.class);
        verify(sentRepository, never()).save(any());
    }

    @Test
    void firstClickEmailGoesToTheOwnersAddress() {
        when(contactRepository.findById("u1")).thenReturn(Optional.of(new Contact("u1", "alice@example.com", Instant.now())));
        when(sentRepository.existsById("milestone:abc:1")).thenReturn(false);

        service.handleMilestone(milestone(1));

        verify(emailSender).send(eq("alice@example.com"),
                eq("Your link http://sho.rt/abc got its first click"),
                contains("clicked 1 time."));
    }

    @Test
    void largeMilestonesAreFormatted() {
        when(contactRepository.findById("u1")).thenReturn(Optional.of(new Contact("u1", "alice@example.com", Instant.now())));
        when(sentRepository.existsById("milestone:abc:10000")).thenReturn(false);

        service.handleMilestone(milestone(10_000));

        verify(emailSender).send(eq("alice@example.com"),
                eq("Your link http://sho.rt/abc reached 10,000 clicks"),
                contains("/api/analytics/links/abc"));
    }

    @Test
    void milestoneBeforeTheContactIsKnownIsRetryable() {
        when(contactRepository.findById("u1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.handleMilestone(milestone(1)))
                .isInstanceOf(ContactNotFoundException.class);
        verifyNoInteractions(emailSender);
    }
}
