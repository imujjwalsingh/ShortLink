package com.shortlink.notificationservice.service;

import com.shortlink.notificationservice.event.LinkMilestoneEvent;
import com.shortlink.notificationservice.event.UserRegisteredEvent;
import com.shortlink.notificationservice.model.Contact;
import com.shortlink.notificationservice.model.SentNotification;
import com.shortlink.notificationservice.repository.ContactRepository;
import com.shortlink.notificationservice.repository.SentNotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.text.NumberFormat;
import java.time.Instant;
import java.util.Locale;

/**
 * Idempotent email sending: check the sent log for the event id, send,
 * then record it. A redelivered event finds its record and is skipped.
 *
 * The one gap is a crash after the SMTP send but before the record is
 * saved; the redelivery then sends a second copy. That's the deliberate
 * choice for this use case: a rare duplicate welcome email is better than
 * recording first and risking an email that never goes out.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final ContactRepository contactRepository;
    private final SentNotificationRepository sentRepository;
    private final EmailSender emailSender;
    private final String publicBaseUrl;

    public NotificationService(ContactRepository contactRepository,
                               SentNotificationRepository sentRepository,
                               EmailSender emailSender,
                               @Value("${app.public-base-url}") String publicBaseUrl) {
        this.contactRepository = contactRepository;
        this.sentRepository = sentRepository;
        this.emailSender = emailSender;
        this.publicBaseUrl = publicBaseUrl;
    }

    public void handleUserRegistered(UserRegisteredEvent event) {
        // Upsert keyed by user id: replaying the event is harmless.
        contactRepository.save(new Contact(event.userId(), event.email(), Instant.now()));
        sendOnce(event.eventId(), "WELCOME", event.email(),
                "Welcome to ShortLink",
                """
                Hi,

                Your ShortLink account (%s) is ready. Create your first short link with
                POST %s/api/urls, and we'll email you when it gets its first click.

                — ShortLink
                """.formatted(event.email(), publicBaseUrl));
    }

    public void handleMilestone(LinkMilestoneEvent event) {
        Contact contact = contactRepository.findById(event.ownerId())
                .orElseThrow(() -> new ContactNotFoundException(event.ownerId()));
        String shortUrl = publicBaseUrl + "/" + event.shortCode();
        String clicks = NumberFormat.getIntegerInstance(Locale.US).format(event.milestone());
        String subject = event.milestone() == 1
                ? "Your link " + shortUrl + " got its first click"
                : "Your link " + shortUrl + " reached " + clicks + " clicks";
        sendOnce(event.eventId(), "MILESTONE", contact.email(), subject,
                """
                Hi,

                %s has now been clicked %s %s.
                See the full breakdown at GET %s/api/analytics/links/%s

                — ShortLink
                """.formatted(shortUrl, clicks, event.milestone() == 1 ? "time" : "times",
                        publicBaseUrl, event.shortCode()));
    }

    private void sendOnce(String eventId, String type, String to, String subject, String body) {
        if (sentRepository.existsById(eventId)) {
            log.debug("Notification for event {} already sent; skipping", eventId);
            return;
        }
        emailSender.send(to, subject, body);
        sentRepository.save(new SentNotification(eventId, type, to, Instant.now()));
        log.info("Sent {} email for event {}", type, eventId);
    }
}
