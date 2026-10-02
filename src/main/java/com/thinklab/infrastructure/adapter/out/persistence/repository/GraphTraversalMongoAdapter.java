package com.thinklab.infrastructure.adapter.out.persistence.repository;

import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.GraphLookupOptions;
import com.mongodb.reactivestreams.client.MongoClient;
import com.thinklab.domain.model.ElementStatus;
import com.thinklab.domain.model.TraversalDirection;
import com.thinklab.domain.port.GraphTraversalPort;
import io.micronaut.context.annotation.Property;
import jakarta.inject.Singleton;
import org.bson.Document;
import org.bson.conversions.Bson;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Blast-radius traversal on MongoDB {@code $graphLookup} (ADR-031). Each edge is stored once, so the two
 * directions are two separate passes over the same {@code edges} collection: DOWNSTREAM follows
 * {@code sourceNodeId -> targetNodeId}, UPSTREAM the reverse. {@code restrictSearchWithMatch} pins the
 * tenant and ACTIVE status at every recursion step, never only on the start document.
 */
@Singleton
public class GraphTraversalMongoAdapter implements GraphTraversalPort {

    private static final String DOWNSTREAM_FIELD = "downstream";
    private static final String UPSTREAM_FIELD = "upstream";

    private final MongoClient mongoClient;
    private final String database;

    public GraphTraversalMongoAdapter(MongoClient mongoClient, @Property(name = "mongodb.uri") String mongoUri) {
        this.mongoClient = mongoClient;
        this.database = MongoSupport.database(mongoUri);
    }

    @Override
    public Mono<TraversalResult> traverse(UUID organisationId, UUID startNodeId, int maxHops, TraversalDirection direction) {
        Bson restrict = Filters.and(Filters.eq("organisationId", organisationId), Filters.eq("status", ElementStatus.ACTIVE.name()));
        GraphLookupOptions options = new GraphLookupOptions().maxDepth(maxHops - 1).depthField("depth").restrictSearchWithMatch(restrict);

        List<Bson> pipeline = new ArrayList<>();
        pipeline.add(Aggregates.match(Filters.and(Filters.eq("_id", startNodeId), Filters.eq("organisationId", organisationId))));
        if (direction != TraversalDirection.UPSTREAM) {
            pipeline.add(Aggregates.graphLookup(MongoSupport.EDGES_COLLECTION, "$_id", "targetNodeId", "sourceNodeId", DOWNSTREAM_FIELD, options));
        }
        if (direction != TraversalDirection.DOWNSTREAM) {
            pipeline.add(Aggregates.graphLookup(MongoSupport.EDGES_COLLECTION, "$_id", "sourceNodeId", "targetNodeId", UPSTREAM_FIELD, options));
        }

        return Mono.from(mongoClient.getDatabase(database).getCollection(MongoSupport.NODES_COLLECTION)
                        .aggregate(pipeline).first())
                .map(doc -> new TraversalResult(hops(doc, DOWNSTREAM_FIELD), hops(doc, UPSTREAM_FIELD)))
                .defaultIfEmpty(new TraversalResult(List.of(), List.of()));
    }

    private static List<EdgeHop> hops(Document doc, String field) {
        List<EdgeHop> result = new ArrayList<>();
        for (Document edge : doc.getList(field, Document.class, List.of())) {
            result.add(new EdgeHop(edge.get("sourceNodeId", UUID.class), edge.get("targetNodeId", UUID.class),
                    ((Number) edge.get("depth")).intValue()));
        }
        return result;
    }
}
