package com.thinklab.infrastructure.adapter.out.persistence.repository;

import com.mongodb.MongoTimeoutException;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.reactivestreams.client.MongoClient;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.context.event.StartupEvent;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Objects;

/**
 * Creates, at startup, the two uniqueness indexes (the atomic backstop for check-then-insert races)
 * and the two traversal indexes ({@code {organisationId, status, sourceNodeId}} and
 * {@code {organisationId, status, targetNodeId}}) that match the exact key order of the two
 * {@code $graphLookup} passes (ADR-031). Fail-open like the kit's initializer: errors are logged and
 * the application still starts.
 */
@Singleton
@Requires(property = "thinklab.mongo.create-indexes", notEquals = "false")
public class TopologyIndexInitializer implements ApplicationEventListener<StartupEvent> {

    static final String NODE_KEY_INDEX = "organisationId_1_nodeType_1_externalId_1";
    static final String EDGE_KEY_INDEX = "organisationId_1_relationshipType_1_sourceNodeId_1_targetNodeId_1";
    static final String EDGE_SOURCE_INDEX = "organisationId_1_status_1_sourceNodeId_1";
    static final String EDGE_TARGET_INDEX = "organisationId_1_status_1_targetNodeId_1";

    private static final Logger log = LoggerFactory.getLogger(TopologyIndexInitializer.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final MongoClient mongoClient;
    private final String database;
    private final Duration timeout;

    @Inject
    public TopologyIndexInitializer(MongoClient mongoClient, @Property(name = "mongodb.uri") String mongoUri) {
        this(mongoClient, mongoUri, TIMEOUT);
    }

    /** Test seam: how long to wait for the server. */
    TopologyIndexInitializer(MongoClient mongoClient, String mongoUri, Duration timeout) {
        this.mongoClient = Objects.requireNonNull(mongoClient, "Infrastructure constraint violated: MongoClient cannot be null.");
        this.database = MongoSupport.database(mongoUri);
        this.timeout = timeout;
    }

    @Override
    public void onApplicationEvent(StartupEvent event) {
        Objects.requireNonNull(event, "Application constraint violated: StartupEvent cannot be null.");
        ensure(MongoSupport.NODES_COLLECTION, new Document("organisationId", 1).append("nodeType", 1).append("externalId", 1), NODE_KEY_INDEX, true);
        ensure(MongoSupport.EDGES_COLLECTION, new Document("organisationId", 1).append("relationshipType", 1)
                .append("sourceNodeId", 1).append("targetNodeId", 1), EDGE_KEY_INDEX, true);
        ensure(MongoSupport.EDGES_COLLECTION, new Document("organisationId", 1).append("status", 1).append("sourceNodeId", 1), EDGE_SOURCE_INDEX, false);
        ensure(MongoSupport.EDGES_COLLECTION, new Document("organisationId", 1).append("status", 1).append("targetNodeId", 1), EDGE_TARGET_INDEX, false);
    }

    private void ensure(String collection, Document keys, String name, boolean unique) {
        try {
            Mono.from(mongoClient.getDatabase(database).getCollection(collection)
                    .createIndex(keys, new IndexOptions().unique(unique).name(name))).block(timeout);
            log.info("[MONGO_INDEXES] Ensured index [{}] on [{}.{}]", name, database, collection);
        } catch (MongoTimeoutException e) {
            log.error("[MONGO_INDEXES] MongoDB unreachable; index [{}] was not created. Reason: {}", name, e.getMessage());
        } catch (RuntimeException e) {
            log.error("[MONGO_INDEXES] Could not create index [{}] on [{}.{}]: {}", name, database, collection, e.getMessage());
        }
    }
}
