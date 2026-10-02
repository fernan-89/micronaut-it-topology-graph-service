package com.thinklab.infrastructure.adapter.out.persistence.repository;

import com.mongodb.MongoClientSettings;
import com.mongodb.MongoWriteException;
import com.mongodb.ServerAddress;
import com.mongodb.WriteError;
import com.mongodb.client.result.InsertOneResult;
import com.mongodb.client.result.UpdateResult;
import com.mongodb.reactivestreams.client.FindPublisher;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;
import com.thinklab.domain.exception.DuplicateEdgeException;
import com.thinklab.domain.exception.EdgeNotFoundException;
import com.thinklab.domain.model.AuditEntry;
import com.thinklab.domain.model.Edge;
import com.thinklab.domain.model.ElementStatus;
import com.thinklab.infrastructure.adapter.out.persistence.entity.EdgeDocument;
import com.thinklab.infrastructure.adapter.out.persistence.entity.EdgeDocument.EdgePersistenceMapper;
import org.bson.BsonDocument;
import org.bson.BsonObjectId;
import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class EdgeMongoRepositoryAdapterTest {

    private static final CodecRegistry REGISTRY = CodecRegistries.withUuidRepresentation(
            CodecRegistries.fromRegistries(MongoClientSettings.getDefaultCodecRegistry(),
                    CodecRegistries.fromProviders(PojoCodecProvider.builder().automatic(true).build())),
            org.bson.UuidRepresentation.STANDARD);

    @Mock private MongoClient mongoClient;
    @Mock private MongoDatabase mongoDatabase;
    @Mock private MongoCollection<EdgeDocument> mongoCollection;

    private EdgeMongoRepositoryAdapter adapter;
    private UUID organisationId;
    private Edge edge;

    @BeforeEach
    void setUp() {
        when(mongoClient.getDatabase("topology_db")).thenReturn(mongoDatabase);
        when(mongoDatabase.getCollection("edges", EdgeDocument.class)).thenReturn(mongoCollection);
        when(mongoCollection.withCodecRegistry(any())).thenReturn(mongoCollection);
        adapter = new EdgeMongoRepositoryAdapter(mongoClient, "mongodb://localhost:27017/topology_db");
        organisationId = UUID.randomUUID();
        edge = Edge.createNew(UUID.randomUUID(), organisationId, "DEPENDS_ON", UUID.randomUUID(), UUID.randomUUID(), "ops");
    }

    private static BsonDocument render(Bson bson) {
        return bson.toBsonDocument(BsonDocument.class, REGISTRY);
    }

    private static MongoWriteException writeError(int code, String message) {
        return new MongoWriteException(new WriteError(code, message, new BsonDocument()), new ServerAddress());
    }

    private void findReturning(Flux<EdgeDocument> documents) {
        FindPublisher<EdgeDocument> publisher = mock(FindPublisher.class);
        when(mongoCollection.find(any(Bson.class))).thenReturn(publisher);
        doAnswer(invocation -> {
            org.reactivestreams.Subscriber<EdgeDocument> subscriber = invocation.getArgument(0);
            documents.subscribe(subscriber);
            return null;
        }).when(publisher).subscribe(any());
    }

    @Test
    @DisplayName("create inserts the mapped document and emits the aggregate")
    void createSuccess() {
        when(mongoCollection.insertOne(any(EdgeDocument.class)))
                .thenReturn(Mono.just(InsertOneResult.acknowledged(new BsonObjectId(new ObjectId()))));

        StepVerifier.create(adapter.create(edge)).expectNextMatches(saved -> saved.getId().equals(edge.getId())).verifyComplete();
    }

    @Test
    @DisplayName("create maps a duplicate on the edge-key index to DuplicateEdgeException")
    void createDuplicate() {
        when(mongoCollection.insertOne(any(EdgeDocument.class))).thenReturn(Mono.error(writeError(11000,
                "E11000 duplicate key error index: organisationId_1_relationshipType_1_sourceNodeId_1_targetNodeId_1 dup key")));

        StepVerifier.create(adapter.create(edge)).expectError(DuplicateEdgeException.class).verify();
    }

    @Test
    @DisplayName("create propagates other write errors, including a duplicate on another index")
    void createOtherErrors() {
        MongoWriteException duplicateId = writeError(11000, "E11000 duplicate key error index: _id_ dup key");
        MongoWriteException validation = writeError(121, "Document failed validation index: organisationId_1_relationshipType_1_sourceNodeId_1_targetNodeId_1");
        IllegalStateException down = new IllegalStateException("mongo down");
        when(mongoCollection.insertOne(any(EdgeDocument.class)))
                .thenReturn(Mono.error(duplicateId)).thenReturn(Mono.error(validation)).thenReturn(Mono.error(down));

        StepVerifier.create(adapter.create(edge)).expectErrorMatches(e -> e == duplicateId).verify();
        StepVerifier.create(adapter.create(edge)).expectErrorMatches(e -> e == validation).verify();
        StepVerifier.create(adapter.create(edge)).expectErrorMatches(e -> e == down).verify();
    }

    @Test
    @DisplayName("findById maps the document back, or completes empty")
    void findById() {
        FindPublisher<EdgeDocument> publisher = mock(FindPublisher.class);
        when(mongoCollection.find(any(Bson.class))).thenReturn(publisher);
        when(publisher.first()).thenReturn(Mono.just(EdgePersistenceMapper.toDocument(edge))).thenReturn(Mono.empty());

        StepVerifier.create(adapter.findById(edge.getId())).expectNextMatches(e -> e.getId().equals(edge.getId())).verifyComplete();
        StepVerifier.create(adapter.findById(edge.getId())).verifyComplete();
    }

    @Test
    @DisplayName("findAllByOrganisationId adds relationshipType, either-endpoint nodeId and status when given")
    void findAllFilters() {
        findReturning(Flux.just(EdgePersistenceMapper.toDocument(edge)));

        StepVerifier.create(adapter.findAllByOrganisationId(organisationId, "DEPENDS_ON", edge.getSourceNodeId(), ElementStatus.ACTIVE))
                .expectNextCount(1).verifyComplete();

        ArgumentCaptor<Bson> captor = ArgumentCaptor.forClass(Bson.class);
        verify(mongoCollection).find(captor.capture());
        String rendered = render(captor.getValue()).toJson();
        assertTrue(rendered.contains("DEPENDS_ON") && rendered.contains("$or") && rendered.contains("sourceNodeId")
                && rendered.contains("targetNodeId") && rendered.contains("ACTIVE"));
    }

    @Test
    @DisplayName("findAllByOrganisationId without optional filters only constrains the tenant")
    void findAllTenantOnly() {
        findReturning(Flux.empty());

        StepVerifier.create(adapter.findAllByOrganisationId(organisationId, null, null, null)).verifyComplete();

        ArgumentCaptor<Bson> captor = ArgumentCaptor.forClass(Bson.class);
        verify(mongoCollection).find(captor.capture());
        String rendered = render(captor.getValue()).toJson();
        assertTrue(rendered.contains("organisationId") && !rendered.contains("relationshipType") && !rendered.contains("$or")
                && !rendered.contains("status"));
    }

    @Test
    @DisplayName("updateStatus $sets status and $pushes the audit entry")
    void updateStatus() {
        when(mongoCollection.updateOne(any(Bson.class), any(Bson.class))).thenReturn(Mono.just(UpdateResult.acknowledged(1, 1L, null)));
        AuditEntry entry = edge.retire("tech");

        StepVerifier.create(adapter.updateStatus(edge.getId(), ElementStatus.RETIRED, entry)).verifyComplete();

        ArgumentCaptor<Bson> update = ArgumentCaptor.forClass(Bson.class);
        verify(mongoCollection).updateOne(any(Bson.class), update.capture());
        BsonDocument doc = render(update.getValue());
        assertEquals("RETIRED", doc.getDocument("$set").getString("status").getValue());
        assertEquals("RETIRED", doc.getDocument("$push").getDocument("auditTrail").getString("action").getValue());
    }

    @Test
    @DisplayName("updateStatus fails with EdgeNotFoundException when no document matches")
    void updateStatusNotFound() {
        when(mongoCollection.updateOne(any(Bson.class), any(Bson.class))).thenReturn(Mono.just(UpdateResult.acknowledged(0, 0L, null)));

        StepVerifier.create(adapter.updateStatus(edge.getId(), ElementStatus.RETIRED, edge.retire("tech")))
                .expectError(EdgeNotFoundException.class).verify();
    }
}
