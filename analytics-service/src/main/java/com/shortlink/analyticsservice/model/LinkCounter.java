package com.shortlink.analyticsservice.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Running click total per link, kept in its own small index. Only used to
 * detect milestones exactly once: Elasticsearch counts on link-clicks are
 * near-real-time (refreshed every ~1s), so "count == 100" there could be
 * skipped or seen twice. A scripted update on a single document is atomic.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LinkCounter(String shortCode, String ownerId, long total) {
}
