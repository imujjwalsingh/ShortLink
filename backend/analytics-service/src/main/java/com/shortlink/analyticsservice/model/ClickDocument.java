package com.shortlink.analyticsservice.model;

import java.time.Instant;

/**
 * One click as stored in the link-clicks index. The document _id is
 * eventId, which is what makes ingestion idempotent.
 *
 * Only derived fields are stored (referrer host, browser/OS/device
 * family, the already-hashed visitor id), never the raw user agent or
 * IP: smaller documents, and nothing sensitive in the analytics store.
 */
public record ClickDocument(
        String eventId,
        String shortCode,
        String ownerId,
        Instant timestamp,
        String referrerHost,
        String browser,
        String os,
        String deviceType,
        String visitorHash
) {
}
