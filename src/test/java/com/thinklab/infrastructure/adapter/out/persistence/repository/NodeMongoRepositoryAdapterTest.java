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
import com.thinklab.domain.exception.DuplicateNodeException;
import com.thinklab.domain.exception.NodeNotFoundException;
import com.thinklab.domain.model.AuditEntry;
import com.thinklab.domain.model.ElementStatus;
import com.thinklab.domain.model.Node;
import com.thinklab.infrastructure.adapter.out.persistence.entity.NodeDocument;
import com.thinklab.infrastructure.adapter.out.persistence.entity.NodeDocument.NodePersistenceMapper;
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

import java.util.List;
import java.util.Map;
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
class NodeMongoRepositoryAdapterTest {

    private static final CodecRegistry REGISTRY = CodecRegistries.withUuidRepresentation(
            CodecRegistries.fromRegistries(MongoClientSettings.getDefaultCodecRegistry(),
                    CodecRegistries.fromProviders(PojoCodecProvider.builder().automatic(true).build())),
            org.bson.UuidRepresentation.STANDARD);

    @Mock private MongoClient mongoClient;
    @Mock private MongoDatabase mongoDatabase;
    @Mock private MongoCollection<NodeDocument> mongoCollection;

    private NodeMongoRepositoryAdapter adapter;
    private UUID organisationId;
    private Node node;

    @BeforeEach
    void setUp() {
        when(mongoClient.getDatabase("topology_db")).thenReturn(mongoDatabase);
        when(mongoDatabase.getCollection("nodes", NodeDocument.class)).thenReturn(mongoCollection);
        when(mongoCollection.withCodecRegistry(any())).thenReturn(mongoCollection);
        adapter = new NodeMongoRepositoryAdapter(mongoClient, "mongodb://localhost:27017/topology_db");
        organisationId = UUID.randomUUID();
        node = Node.createNew(UUID.randomUUID(), organisationId, "ASSET", UUID.randomUUID(), "sw", Map.of("rack", "A"), "ops");
    }

    private static BsonDocument render(Bson bson) {
        return bson.toBsonDocument(BsonDocument.class, REGISTRY);
    }

    private static MongoWriteException writeError(int code, String message) {
        return new MongoWriteException(new WriteError(code, message, new BsonDocument()), new ServerAddress());
    }

    private FindPublisher<NodeDocument> findReturning(Flux<NodeDocument> documents) {
        FindPublisher<NodeDocument> publisher = mock(FindPublisher.class);
        when(mongoCollection.find(any(Bson.class))).thenReturn(publisher);
        doAnswer(invocation -> {
            org.reactivestreams.Subscriber<NodeDocument> subscriber = invocation.getArgument(0);
            documents.subscribe(subscriber);
            return null;
        }).when(publisher).subscribe(any());
        return publisher;
    }

    @Test
    @DisplayName("create inserts the mapped document and emits the aggregate")
    void createSuccess() {
        when(mongoCollection.insertOne(any(NodeDocument.class)))
                .thenReturn(Mono.just(InsertOneResult.acknowledged(new BsonObjectId(new ObjectId()))));

        StepVerifier.create(adapter.create(node)).expectNextMatches(saved -> saved.getId().equals(node.getId())).verifyComplete();

        ArgumentCaptor<NodeDocument> captor = ArgumentCaptor.forClass(NodeDocument.class);
        verify(mongoCollection).insertOne(captor.capture());
        assertEquals("ACTIVE", captor.getValue().getStatus());
    }

    @Test
    @DisplayName("create maps a duplicate on the node-key index to DuplicateNodeException")
    void createDuplicate() {
        when(mongoCollection.insertOne(any(NodeDocument.class))).thenReturn(Mono.error(writeError(11000,
                "E11000 duplicate key error index: organisationId_1_nodeType_1_externalId_1 dup key")));

        StepVerifier.create(adapter.create(node)).expectError(DuplicateNodeException.class).verify();
    }

    @Test
    @DisplayName("create propagates other write errors, including a duplicate on another index")
    void createOtherErrors() {
        MongoWriteException duplicateId = writeError(11000, "E11000 duplicate key error index: _id_ dup key");
        MongoWriteException validation = writeError(121, "Document failed validation index: organisationId_1_nodeType_1_externalId_1");
        IllegalStateException down = new IllegalStateException("mongo down");
        when(mongoCollection.insertOne(any(NodeDocument.class)))
                .thenReturn(Mono.error(duplicateId)).thenReturn(Mono.error(validation)).thenReturn(Mono.error(down));

        StepVerifier.create(adapter.create(node)).expectErrorMatches(e -> e == duplicateId).verify();
        StepVerifier.create(adapter.create(node)).expectErrorMatches(e -> e == validation).verify();
        StepVerifier.create(adapter.create(node)).expectErrorMatches(e -> e == down).verify();
    }

    @Test
    @DisplayName("findById maps the document back, or completes empty")
    void findById() {
        FindPublisher<NodeDocument> publisher = mock(FindPublisher.class);
        when(mongoCollection.find(any(Bson.class))).thenReturn(publisher);
        when(publisher.first()).thenReturn(Mono.just(NodePersistenceMapper.toDocument(node))).thenReturn(Mono.empty());

        StepVerifier.create(adapter.findById(node.getId())).expectNextMatches(n -> n.getId().equals(node.getId())).verifyComplete();
        StepVerifier.create(adapter.findById(node.getId())).verifyComplete();
    }

    @Test
    @DisplayName("findAllByOrganisationId always filters by tenant and adds nodeType/status when given")
    void findAllFilters() {
        findReturning(Flux.just(NodePersistenceMapper.toDocument(node)));

        StepVerifier.create(adapter.findAllByOrganisationId(organisationId, "ASSET", ElementStatus.ACTIVE)).expectNextCount(1).verifyComplete();

        ArgumentCaptor<Bson> captor = ArgumentCaptor.forClass(Bson.class);
        verify(mongoCollection).find(captor.capture());
        String rendered = render(captor.getValue()).toJson();
        assertTrue(rendered.contains("organisationId") && rendered.contains("ASSET") && rendered.contains("ACTIVE"));
    }

    @Test
    @DisplayName("findAllByOrganisationId without optional filters only constrains the tenant")
    void findAllTenantOnly() {
        findReturning(Flux.empty());

        StepVerifier.create(adapter.findAllByOrganisationId(organisationId, null, null)).verifyComplete();

        ArgumentCaptor<Bson> captor = ArgumentCaptor.forClass(Bson.class);
        verify(mongoCollection).find(captor.capture());
        String rendered = render(captor.getValue()).toJson();
        assertTrue(rendered.contains("organisationId") && !rendered.contains("nodeType") && !rendered.contains("status"));
    }

    @Test
    @DisplayName("findActiveByOrganisationIdAndIds is tenant-scoped, ACTIVE-only and filtered by id")
    void findActiveByIds() {
        findReturning(Flux.just(NodePersistenceMapper.toDocument(node)));

        StepVerifier.create(adapter.findActiveByOrganisationIdAndIds(organisationId, List.of(node.getId()))).expectNextCount(1).verifyComplete();

        ArgumentCaptor<Bson> captor = ArgumentCaptor.forClass(Bson.class);
        verify(mongoCollection).find(captor.capture());
        String rendered = render(captor.getValue()).toJson();
        assertTrue(rendered.contains("organisationId") && rendered.contains("$in") && rendered.contains("ACTIVE"));
    }

    @Test
    @DisplayName("updateInfo $sets label/attributes/updatedAt and $pushes the audit entry")
    void updateInfo() {
        when(mongoCollection.updateOne(any(Bson.class), any(Bson.class))).thenReturn(Mono.just(UpdateResult.acknowledged(1, 1L, null)));
        AuditEntry entry = node.update("sw2", Map.of("rack", "B"), "tech");

        StepVerifier.create(adapter.updateInfo(node.getId(), "sw2", Map.of("rack", "B"), entry)).verifyComplete();

        ArgumentCaptor<Bson> update = ArgumentCaptor.forClass(Bson.class);
        verify(mongoCollection).updateOne(any(Bson.class), update.capture());
        BsonDocument doc = render(update.getValue());
        assertEquals("sw2", doc.getDocument("$set").getString("label").getValue());
        assertTrue(doc.getDocument("$set").containsKey("attributes"));
        assertEquals("UPDATED", doc.getDocument("$push").getDocument("auditTrail").getString("action").getValue());
    }

    @Test
    @DisplayName("updateStatus $sets status and $pushes the audit entry")
    void updateStatus() {
        when(mongoCollection.updateOne(any(Bson.class), any(Bson.class))).thenReturn(Mono.just(UpdateResult.acknowledged(1, 1L, null)));
        AuditEntry entry = node.retire("tech");

        StepVerifier.create(adapter.updateStatus(node.getId(), ElementStatus.RETIRED, entry)).verifyComplete();

        ArgumentCaptor<Bson> update = ArgumentCaptor.forClass(Bson.class);
        verify(mongoCollection).updateOne(any(Bson.class), update.capture());
        BsonDocument doc = render(update.getValue());
        assertEquals("RETIRED", doc.getDocument("$set").getString("status").getValue());
        assertEquals("RETIRED", doc.getDocument("$push").getDocument("auditTrail").getString("action").getValue());
    }

    @Test
    @DisplayName("every partial update fails with NodeNotFoundException when no document matches")
    void updatesFailWhenNothingMatches() {
        when(mongoCollection.updateOne(any(Bson.class), any(Bson.class))).thenReturn(Mono.just(UpdateResult.acknowledged(0, 0L, null)));
        AuditEntry entry = node.retire("tech");

        StepVerifier.create(adapter.updateStatus(node.getId(), ElementStatus.RETIRED, entry)).expectError(NodeNotFoundException.class).verify();
        StepVerifier.create(adapter.updateInfo(node.getId(), "l", Map.of(), entry)).expectError(NodeNotFoundException.class).verify();
    }
}
