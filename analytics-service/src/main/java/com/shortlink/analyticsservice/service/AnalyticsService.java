package com.shortlink.analyticsservice.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.CalendarInterval;
import co.elastic.clients.elasticsearch._types.aggregations.FieldDateMath;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.json.JsonData;
import com.shortlink.analyticsservice.dto.CountBucket;
import com.shortlink.analyticsservice.dto.DailyCount;
import com.shortlink.analyticsservice.dto.LinkClicks;
import com.shortlink.analyticsservice.dto.LinkStatsResponse;
import com.shortlink.analyticsservice.dto.TopLinksResponse;
import com.shortlink.analyticsservice.repository.ClickRepository;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;

/**
 * Read side: every stats response is a single size=0 search with
 * aggregations, so Elasticsearch does the counting and only buckets come
 * back over the wire.
 *
 * Every query filters on ownerId = the caller's token subject. Asking for
 * a link you don't own therefore returns zero counts, indistinguishable
 * from a link nobody has clicked, so the endpoint can't be used to probe
 * other accounts' traffic.
 *
 * uniqueVisitors uses the cardinality aggregation (HyperLogLog++): exact up
 * to ~3000 distinct visitors, then approximate within a few percent, which
 * is the standard trade for not keeping every visitor id in memory.
 * Day buckets are UTC.
 */
@Service
public class AnalyticsService {

    static final int TOP_N = 10;

    private final ElasticsearchClient client;
    private final ClickRepository clickRepository;

    public AnalyticsService(ElasticsearchClient client, ClickRepository clickRepository) {
        this.client = client;
        this.clickRepository = clickRepository;
    }

    public LinkStatsResponse linkStats(String shortCode, String ownerId, int days) {
        clickRepository.ensureSchema();
        String from = "now-" + (days - 1) + "d/d";
        try {
            SearchResponse<Void> response = client.search(s -> s
                            .index(clickRepository.clicksIndex())
                            .size(0)
                            .trackTotalHits(t -> t.enabled(true))
                            .query(q -> q.bool(b -> b
                                    .filter(f -> f.term(t -> t.field("shortCode").value(shortCode)))
                                    .filter(f -> f.term(t -> t.field("ownerId").value(ownerId)))))
                            .aggregations("unique_visitors", a -> a.cardinality(c -> c.field("visitorHash")))
                            .aggregations("referrers", a -> a.terms(t -> t.field("referrerHost").size(TOP_N)))
                            .aggregations("browsers", a -> a.terms(t -> t.field("browser").size(TOP_N)))
                            .aggregations("operating_systems", a -> a.terms(t -> t.field("os").size(TOP_N)))
                            .aggregations("devices", a -> a.terms(t -> t.field("deviceType").size(TOP_N)))
                            .aggregations("recent", a -> a
                                    .filter(f -> f.range(r -> r.field("timestamp").gte(JsonData.of(from))))
                                    .aggregations("per_day", d -> d.dateHistogram(h -> h
                                            .field("timestamp")
                                            .calendarInterval(CalendarInterval.Day)
                                            .format("yyyy-MM-dd")
                                            .minDocCount(0)
                                            .extendedBounds(eb -> eb
                                                    .min(FieldDateMath.of(m -> m.expr(from)))
                                                    .max(FieldDateMath.of(m -> m.expr("now/d"))))))),
                    Void.class);

            Map<String, Aggregate> aggs = response.aggregations();
            List<DailyCount> perDay = aggs.get("recent").filter().aggregations().get("per_day")
                    .dateHistogram().buckets().array().stream()
                    .map(b -> new DailyCount(b.keyAsString(), b.docCount()))
                    .toList();

            return new LinkStatsResponse(
                    shortCode,
                    totalHits(response),
                    (long) aggs.get("unique_visitors").cardinality().value(),
                    days,
                    perDay,
                    terms(aggs.get("referrers")),
                    terms(aggs.get("browsers")),
                    terms(aggs.get("operating_systems")),
                    terms(aggs.get("devices")));
        } catch (IOException e) {
            throw new UncheckedIOException("Elasticsearch unavailable", e);
        }
    }

    public TopLinksResponse topLinks(String ownerId, int limit) {
        clickRepository.ensureSchema();
        try {
            SearchResponse<Void> response = client.search(s -> s
                            .index(clickRepository.clicksIndex())
                            .size(0)
                            .trackTotalHits(t -> t.enabled(true))
                            .query(q -> q.term(t -> t.field("ownerId").value(ownerId)))
                            .aggregations("links", a -> a.terms(t -> t.field("shortCode").size(limit))),
                    Void.class);
            List<LinkClicks> links = response.aggregations().get("links").sterms().buckets().array().stream()
                    .map(b -> new LinkClicks(b.key().stringValue(), b.docCount()))
                    .toList();
            return new TopLinksResponse(totalHits(response), links);
        } catch (IOException e) {
            throw new UncheckedIOException("Elasticsearch unavailable", e);
        }
    }

    private static long totalHits(SearchResponse<Void> response) {
        return response.hits().total() == null ? 0 : response.hits().total().value();
    }

    private static List<CountBucket> terms(Aggregate aggregate) {
        return aggregate.sterms().buckets().array().stream()
                .map(b -> new CountBucket(fieldValue(b.key()), b.docCount()))
                .toList();
    }

    private static String fieldValue(FieldValue value) {
        return value.isString() ? value.stringValue() : String.valueOf(value._get());
    }
}
