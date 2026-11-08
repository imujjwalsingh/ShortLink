# notification-service

Sends a welcome email on `user-registered` and a "your link hit N clicks"
email on `link-milestones` (1, 10, 100, …). No public API; port 8084 serves
actuator only. In docker-compose all mail goes to Mailpit
(http://localhost:8025).

## Design decisions (interview talking points)

**Event-carried state transfer instead of a synchronous lookup.** Milestone
events carry only an owner id. Rather than calling auth-service for the
email address (coupling notifications to auth's uptime), this service builds
its own `contacts` table from `user-registered` events. It reads that topic
from the earliest offset, so a fresh deployment rebuilds the table.

**Idempotent sending.** Each sent email is recorded under its triggering
event's id; a redelivered event finds the record and is skipped. The order
is check → send → record: a crash between send and record can produce a
duplicate email, which is the chosen side of the trade-off (recording
first could lose an email entirely).

**Cross-topic ordering.** A milestone can arrive before the owner's
`user-registered` event has been processed. That raises a retryable
`ContactNotFoundException`, and the error handler backs off and retries.
Malformed events go straight to `<topic>.DLT`; SMTP failures retry for up to
2 minutes and then dead-letter.

**JavaMail timeouts are set explicitly** (5 s). Its defaults are infinite,
so one hung SMTP server would otherwise freeze the listener thread.
