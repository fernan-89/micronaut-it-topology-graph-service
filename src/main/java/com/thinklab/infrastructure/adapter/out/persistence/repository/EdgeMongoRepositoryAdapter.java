package com.thinklab.infrastructure.adapter.out.persistence.repository;

import com.mongodb.ErrorCategory;
import com.mongodb.MongoWriteException;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.thinklab.domain.exception.DuplicateEdgeException;
import com.thinklab.domain.exception.EdgeNotFoundException;
import com.thinklab.domain.model.AuditEntry;
import com.thinklab.domain.model.Edge;
import com.thinklab.domain.model.ElementStatus;
import com.thinklab.domain.repository.EdgeRepository;
import com.thinklab.infrastructure.adapter.out.persistence.entity.AuditEntryDocument;
import com.thinklab.infrastructure.adapter.out.persistence.entity.EdgeDocument;
import com.thinklab.infrastructure.adapter.out.persistence.entity.EdgeDocument.EdgePersistenceMapper;
import io.micronaut.context.annotation.Property;
import jakarta.inject.Singleton;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** MongoDB Reactive adapter for {@link EdgeRepository}: every transition is one atomic $set + $push. */
@Singleton
public class EdgeMongoRepositoryAdapter implements EdgeRepository {

    private static final Logger log = LoggerFactory.getLogger(EdgeMongoRepositoryAdapter.class);
    private static final String FIELD_ID = "_id";

    private final MongoClient mongoClient;
    private final String database;

    public EdgeMongoRepositoryAdapter(MongoClient mongoClient, @Property(name = "mongodb.uri") String mongoUri) {
        this.mongoClient = mongoClient;
        this.database = MongoSupport.database(mongoUri);
    }

    private MongoCollection<EdgeDocument> getCollection() {
        return mongoClient.getDatabase(database)
                .getCollection(MongoSupport.EDGES_COLLECTION, EdgeDocument.class)
                .withCodecRegistry(MongoSupport.POJO_CODEC_REGISTRY);
    }

    @Override
    public Mono<Edge> create(Edge edge) {
        log.debug("[PERSISTENCE] Monolithic create for Edge Aggregate: {}", edge.getId());
        return Mono.from(getCollection().insertOne(EdgePersistenceMapper.toDocument(edge)))
                .map(result -> edge)
                .onErrorMap(EdgeMongoRepositoryAdapter::isDuplicateEdgeKey, e -> new DuplicateEdgeException(String.format(
                        "An Edge [%s] already exists from node [%s] to node [%s] for organisation [%s].",
                        edge.getRelationshipType(), edge.getSourceNodeId(), edge.getTargetNodeId(), edge.getOrganisationId())));
    }

    private static boolean isDuplicateEdgeKey(Throwable error) {
        return error instanceof MongoWriteException write
                && write.getError().getCategory() == ErrorCategory.DUPLICATE_KEY
                && write.getError().getMessage().contains(TopologyIndexInitializer.EDGE_KEY_INDEX);
    }

    @Override
    public Mono<Edge> findById(UUID id) {
        return Mono.from(getCollection().find(Filters.eq(FIELD_ID, id)).first()).map(EdgePersistenceMapper::toDomain);
    }

    @Override
    public Flux<Edge> findAllByOrganisationId(UUID organisationId, String relationshipType, UUID nodeId, ElementStatus status) {
        List<Bson> filters = new ArrayList<>();
        filters.add(Filters.eq("organisationId", organisationId));
        if (relationshipType != null) {
            filters.add(Filters.eq("relationshipType", relationshipType));
        }
        if (nodeId != null) {
            filters.add(Filters.or(Filters.eq("sourceNodeId", nodeId), Filters.eq("targetNodeId", nodeId)));
        }
        if (status != null) {
            filters.add(Filters.eq("status", status.name()));
        }
        return Flux.from(getCollection().find(Filters.and(filters))).map(EdgePersistenceMapper::toDomain);
    }

    @Override
    public Mono<Void> updateStatus(UUID id, ElementStatus status, AuditEntry auditEntry) {
        Bson update = Updates.combine(
                Updates.set("status", status.name()),
                Updates.set("updatedAt", Instant.now()),
                Updates.push("auditTrail", AuditEntryDocument.fromDomain(auditEntry)));
        return Mono.from(getCollection().updateOne(Filters.eq(FIELD_ID, id), update))
                .flatMap(result -> result.getMatchedCount() == 0
                        ? Mono.error(new EdgeNotFoundException(id))
                        : Mono.<Void>empty());
    }
}
