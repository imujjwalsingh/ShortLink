package com.shortlink.urlservice.repository;

import com.shortlink.urlservice.model.Counter;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import static org.springframework.data.mongodb.core.query.Criteria.where;

@Repository
public class CounterRepository {

    private static final String SEQUENCE_NAME = "url_seq";

    private final MongoTemplate mongoTemplate;

    public CounterRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * Atomically increments and returns the next value of the shared
     * sequence. findAndModify with upsert=true means the first call ever
     * made creates the counter document, no manual seeding required.
     */
    public long getNextSequence() {
        Query query = Query.query(where("_id").is(SEQUENCE_NAME));
        Update update = new Update().inc("seq", 1);
        FindAndModifyOptions options = FindAndModifyOptions.options()
                .returnNew(true)
                .upsert(true);

        Counter counter = mongoTemplate.findAndModify(query, update, options, Counter.class);
        return counter != null ? counter.getSeq() : 1L;
    }
}
