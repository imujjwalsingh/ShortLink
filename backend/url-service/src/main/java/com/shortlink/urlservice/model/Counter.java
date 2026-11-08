package com.shortlink.urlservice.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Single-document counter used to hand out monotonically increasing IDs,
 * which get base62-encoded into short codes.
 *
 * MongoDB has no native auto-increment, so this is the standard workaround:
 * one document per sequence name, incremented via findAndModify with
 * returnNew=true, which Mongo executes atomically. Under concurrent writes
 * two requests can never get the same seq value — no collision handling
 * needed on this path (that's the trade-off vs. a hash-based approach,
 * which would need a collision-retry loop instead).
 */
@Document(collection = "counters")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Counter {

    @Id
    private String id; // sequence name, e.g. "url_seq"

    private long seq;
}
