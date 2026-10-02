package com.thinklab.infrastructure.adapter.out.persistence.repository;

import com.mongodb.ErrorCategory;
import com.mongodb.MongoWriteException;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.thinklab.domain.exception.DuplicateNodeException;
import com.thinklab.domain.exception.NodeNotFoundException;
import com.thinklab.domain.model.AuditEntry;
import com.thinklab.domain.model.ElementStatus;
import com.thinklab.domain.model.Node;
import com.thinklab.domain.repository.NodeRepository;
import com.thinklab.infrastructure.adapter.out.persistence.entity.AuditEntryDocument;
import com.thinklab.infrastructure.adapter.out.persistence.entity.NodeDocument;
import com.thinklab.infrastructure.adapter.out.persistence.entity.NodeDocument.NodePersistenceMapper;
import io.micronaut.context.annotation.Property;
import jakarta.inject.Singleton;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** MongoDB Reactive adapter for {@link NodeRepository}: every transition is one atomic $set + $push. */
@Singleton
public class NodeMongoRepositoryAdapter implements NodeRepository {

    private static final Logger log = LoggerFactory.getLogger(NodeMongoRepositoryAdapter.class);
    private static final String FIELD_ID = "_id";
    private static final String FIELD_ORGANISATION = "organisationId";
    private static final String FIELD_STATUS = "status";
    private static final String FIELD_UPDATED_AT = "updatedAt";
    private static final String FIELD_AUDIT_TRAIL = "auditTrail";

    private final MongoClient mongoClient;
    private final String database;

    public NodeMongoRepositoryAdapter(MongoClient mongoClient, @Property(name = "mongodb.uri") String mongoUri) {
        this.mongoClient = mongoClient;
        this.database = MongoSupport.database(mongoUri);
    }

    private MongoCollection<NodeDocument> getCollection() {
        return mongoClient.getDatabase(database)
                .getCollection(MongoSupport.NODES_COLLECTION, NodeDocument.class)
                .withCodecRegistry(MongoSupport.POJO_CODEC_REGISTRY);
    }

    @Override
    public Mono<Node> create(Node node) {
        log.debug("[PERSISTENCE] Monolithic create for Node Aggregate: {}", node.getId());
        return Mono.from(getCollection().insertOne(NodePersistenceMapper.toDocument(node)))
                .map(result -> node)
                .onErrorMap(NodeMongoRepositoryAdapter::isDuplicateNodeKey, e -> new DuplicateNodeException(String.format(
                        "A Node already exists for organisation [%s], type [%s] and externalId [%s].",
                        node.getOrganisationId(), node.getNodeType(), node.getExternalId())));
    }

    private static boolean isDuplicateNodeKey(Throwable error) {
        return error instanceof MongoWriteException write
                && write.getError().getCategory() == ErrorCategory.DUPLICATE_KEY
                && write.getError().getMessage().contains(TopologyIndexInitializer.NODE_KEY_INDEX);
    }

    @Override
    public Mono<Node> findById(UUID id) {
        return Mono.from(getCollection().find(Filters.eq(FIELD_ID, id)).first()).map(NodePersistenceMapper::toDomain);
    }

    @Override
    public Flux<Node> findAllByOrganisationId(UUID organisationId, String nodeType, ElementStatus status) {
        List<Bson> filters = new ArrayList<>();
        filters.add(Filters.eq(FIELD_ORGANISATION, organisationId));
        if (nodeType != null) {
            filters.add(Filters.eq("nodeType", nodeType));
        }
        if (status != null) {
            filters.add(Filters.eq(FIELD_STATUS, status.name()));
        }
        return Flux.from(getCollection().find(Filters.and(filters))).map(NodePersistenceMapper::toDomain);
    }

    @Override
    public Flux<Node> findActiveByOrganisationIdAndIds(UUID organisationId, Collection<UUID> ids) {
        Bson filter = Filters.and(Filters.eq(FIELD_ORGANISATION, organisationId), Filters.in(FIELD_ID, ids),
                Filters.eq(FIELD_STATUS, ElementStatus.ACTIVE.name()));
        return Flux.from(getCollection().find(filter)).map(NodePersistenceMapper::toDomain);
    }

    @Override
    public Mono<Void> updateInfo(UUID id, String label, Map<String, String> attributes, AuditEntry auditEntry) {
        return executeUpdate(id, Updates.combine(
                Updates.set("label", label),
                Updates.set("attributes", new LinkedHashMap<>(attributes)),
                Updates.set(FIELD_UPDATED_AT, Instant.now()),
                Updates.push(FIELD_AUDIT_TRAIL, AuditEntryDocument.fromDomain(auditEntry))));
    }

    @Override
    public Mono<Void> updateStatus(UUID id, ElementStatus status, AuditEntry auditEntry) {
        return executeUpdate(id, Updates.combine(
                Updates.set(FIELD_STATUS, status.name()),
                Updates.set(FIELD_UPDATED_AT, Instant.now()),
                Updates.push(FIELD_AUDIT_TRAIL, AuditEntryDocument.fromDomain(auditEntry))));
    }

    private Mono<Void> executeUpdate(UUID id, Bson update) {
        return Mono.from(getCollection().updateOne(Filters.eq(FIELD_ID, id), update))
                .flatMap(result -> result.getMatchedCount() == 0
                        ? Mono.error(new NodeNotFoundException(id))
                        : Mono.<Void>empty());
    }
}
