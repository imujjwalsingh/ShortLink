package com.shortlink.analyticsservice.dto;

import java.util.List;

/**
 * totalClicks and uniqueVisitors are all-time; clicksPerDay covers the
 * last {@code days} days (UTC), including days with zero clicks.
 */
public record LinkStatsResponse(
        String shortCode,
        long totalClicks,
        long uniqueVisitors,
        int days,
        List<DailyCount> clicksPerDay,
        List<CountBucket> topReferrers,
        List<CountBucket> browsers,
        List<CountBucket> operatingSystems,
        List<CountBucket> devices
) {
}
