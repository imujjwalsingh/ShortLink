package com.shortlink.analyticsservice.repository;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.mapping.DynamicMapping;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import co.elastic.clients.elasticsearch.core.UpdateResponse;
import co.elastic.clients.elasticsearch._types.InlineGet;
import co.elastic.clients.util.ObjectBuilder;
import com.shortlink.analyticsservice.model.ClickDocument;
import com.shortlink.analyticsservice.model.LinkCounter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * Elasticsearch access for the ingestion side, plus schema management.
 *
 * Schema: each index gets an explicit, strict mapping (keyword fields for
 * exact-match filters and terms aggregations, a real date field for the
 * histogram), installed both as an index TEMPLATE and by creating the index.
 * The template matters because indexing into a missing index auto-creates
 * it, and without a template it would get dynamic mappings (text +
 * keyword sub-fields) that break the term filters the stats API relies on.
 * With the template, even an auto-created index comes out right.
 */
@Repository
public class ClickRepository {

    private static final Logger log = LoggerFactory.getLogger(ClickRepository.class);

    private final ElasticsearchClient client;
    private final String clicksIndex;
    private final String countersIndex;
    private final String replicas;
    private final AtomicBoolean schemaReady = new AtomicBoolean();

    public ClickRepository(ElasticsearchClient client,
                           @Value("${app.elasticsearch.clicks-index}") String clicksIndex,
                           @Value("${app.elasticsearch.counters-index}") String countersIndex,
                           @Value("${app.elasticsearch.number-of-replicas:0}") int replicas) {
        this.client = client;
        this.clicksIndex = clicksIndex;
        this.countersIndex = countersIndex;
        this.replicas = Integer.toString(replicas);
    }

    public String clicksIndex() {
        return clicksIndex;
    }

    /** Best effort at startup; ingestion and queries retry lazily if ES isn't up yet. */
    @EventListener(ApplicationReadyEvent.class)
    public void initSchemaOnStartup() {
        try {
            ensureSchema();
        } catch (RuntimeException e) {
            log.warn("Elasticsearch schema setup deferred (will retry on first use): {}", e.toString());
        }
    }

    public void ensureSchema() {
        if (schemaReady.get()) {
            return;
        }
        try {
            installIndex(clicksIndex, m -> m
                    .dynamic(DynamicMapping.Strict)
                    .properties("eventId", p -> p.keyword(k -> k))
                    .properties("shortCode", p -> p.keyword(k -> k))
                    .properties("ownerId", p -> p.keyword(k -> k))
                    .properties("timestamp", p -> p.date(d -> d))
                    .properties("referrerHost", p -> p.keyword(k -> k))
                    .properties("browser", p -> p.keyword(k -> k))
                    .properties("os", p -> p.keyword(k -> k))
                    .properties("deviceType", p -> p.keyword(k -> k))
                    .properties("visitorHash", p -> p.keyword(k -> k)));
            installIndex(countersIndex, m -> m
                    .dynamic(DynamicMapping.Strict)
                    .properties("shortCode", p -> p.keyword(k -> k))
                    .properties("ownerId", p -> p.keyword(k -> k))
                    .properties("total", p -> p.long_(l -> l)));
            schemaReady.set(true);
        } catch (IOException e) {
            throw new UncheckedIOException("Elasticsearch unavailable while installing schema", e);
        }
    }

    private void installIndex(String index, Function<TypeMapping.Builder, ObjectBuilder<TypeMapping>> mapping)
            throws IOException {
        client.indices().putIndexTemplate(t -> t
                .name(index + "-template")
                .indexPatterns(index)
                .template(tpl -> tpl
                        .settings(s -> s.numberOfShards("1").numberOfReplicas(replicas))
                        .mappings(mapping)));
        if (client.indices().exists(e -> e.index(index)).value()) {
            return;
        }
        try {
            client.indices().create(c -> c.index(index));
            log.info("Created Elasticsearch index {}", index);
        } catch (ElasticsearchException e) {
            // Another instance won the race; the template guarantees the mapping either way.
            if (!"resource_already_exists_exception".equals(e.error().type())) {
                throw e;
            }
        }
    }

    /**
     * Indexes the click with op_type=create and _id = eventId.
     *
     * @return true if this is the first time we've seen the event, false if
     *         a document with that id already exists (a Kafka redelivery).
     */
    public boolean createIfAbsent(ClickDocument doc) {
        ensureSchema();
        try {
            client.create(c -> c.index(clicksIndex).id(doc.eventId()).document(doc));
            return true;
        } catch (ElasticsearchException e) {
            if (e.status() == 409) {
                return false;
            }
            throw e;
        } catch (IOException e) {
            throw new UncheckedIOException("Elasticsearch unavailable while indexing click", e);
        }
    }

    /**
     * Atomically increments the link's running total and returns the new
     * value. Upsert creates the counter at 1 on the first click; after that
     * a painless script increments it, with Elasticsearch retrying
     * internally on version conflicts between concurrent updates.
     */
    public long incrementCounter(String shortCode, String ownerId) {
        try {
            UpdateResponse<LinkCounter> response = client.update(u -> u
                            .index(countersIndex)
                            .id(shortCode)
                            .script(s -> s.inline(i -> i.lang("painless").source("ctx._source.total += 1")))
                            .upsert(new LinkCounter(shortCode, ownerId, 1))
                            .retryOnConflict(10)
                            .source(src -> src.fetch(true)),
                    LinkCounter.class);
            InlineGet<LinkCounter> result = response.get();
            if (result == null || result.source() == null) {
                throw new IllegalStateException("Counter update for " + shortCode + " returned no source");
            }
            return result.source().total();
        } catch (IOException e) {
            throw new UncheckedIOException("Elasticsearch unavailable while updating counter", e);
        }
    }
}
