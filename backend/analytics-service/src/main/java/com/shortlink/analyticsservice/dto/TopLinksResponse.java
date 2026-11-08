package com.shortlink.analyticsservice.dto;

import java.util.List;

/** totalClicks is across all of the caller's links; links is the top N by clicks. */
public record TopLinksResponse(long totalClicks, List<LinkClicks> links) {
}
