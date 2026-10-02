package com.thinklab.infrastructure.adapter.out.persistence.repository;

import com.mongodb.MongoClientSettings;
import com.mongodb.reactivestreams.client.AggregatePublisher;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;
import com.thinklab.domain.model.TraversalDirection;
import com.thinklab.domain.port.GraphTraversalPort.EdgeHop;
import com.thinklab.domain.port.GraphTraversalPort.TraversalResult;
import org.bson.BsonDocument;
import org.bson.Document;
import org.bson.codecs.configuration.CodecRegistries;
import org.bson.conversions.Bson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings({"unchecked", "rawtypes"})
class GraphTraversalMongoAdapterTest {

    private MongoCollection<Document> nodes;
    private AggregatePublisher<Document> aggregate;
    private GraphTraversalMongoAdapter adapter;
    private UUID organisationId;
    private UUID start;

    @BeforeEach
    void setUp() {
        MongoClient client = mock(MongoClient.class);
        MongoDatabase database = mock(MongoDatabase.class);
        nodes = mock(MongoCollection.class);
        aggregate = mock(AggregatePublisher.class);
        when(client.getDatabase("topology_db")).thenReturn(database);
        when(database.getCollection("nodes")).thenReturn(nodes);
        when(nodes.aggregate(anyList())).thenReturn(aggregate);
        adapter = new GraphTraversalMongoAdapter(client, "mongodb://localhost:27017/topology_db");
        organisationId = UUID.randomUUID();
        start = UUID.randomUUID();
    }

    private List<BsonDocument> capturedStages() {
        ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
        verify(nodes).aggregate(captor.capture());
        return ((List<Bson>) captor.getValue()).stream()
                .map(stage -> stage.toBsonDocument(BsonDocument.class, CodecRegistries.withUuidRepresentation(
                        MongoClientSettings.getDefaultCodecRegistry(), org.bson.UuidRepresentation.STANDARD)))
                .toList();
    }

    private static Document edge(UUID source, UUID target, long depth) {
        return new Document("sourceNodeId", source).append("targetNodeId", target).append("depth", depth);
    }

    @Test
    @DisplayName("BOTH runs the two $graphLookup passes with tenant + ACTIVE pinned at every recursion step")
    void bothDirections() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        when(aggregate.first()).thenReturn(Mono.just(new Document()
                .append("downstream", List.of(edge(start, a, 0L), edge(a, b, 1L)))
                .append("upstream", List.of(edge(c, start, 0L)))));

        StepVerifier.create(adapter.traverse(organisationId, start, 3, TraversalDirection.BOTH))
                .assertNext(result -> {
                    assertEquals(List.of(new EdgeHop(start, a, 0), new EdgeHop(a, b, 1)), result.downstream());
                    assertEquals(List.of(new EdgeHop(c, start, 0)), result.upstream());
                })
                .verifyComplete();

        List<BsonDocument> stages = capturedStages();
        assertEquals(3, stages.size());
        BsonDocument downstream = stages.get(1).getDocument("$graphLookup");
        assertEquals("edges", downstream.getString("from").getValue());
        assertEquals("targetNodeId", downstream.getString("connectFromField").getValue());
        assertEquals("sourceNodeId", downstream.getString("connectToField").getValue());
        assertEquals("downstream", downstream.getString("as").getValue());
        assertEquals(2, downstream.getNumber("maxDepth").intValue());
        assertEquals("depth", downstream.getString("depthField").getValue());
        String restrict = downstream.getDocument("restrictSearchWithMatch").toJson();
        assertTrue(restrict.contains("organisationId") && restrict.contains("ACTIVE"));
        BsonDocument upstream = stages.get(2).getDocument("$graphLookup");
        assertEquals("sourceNodeId", upstream.getString("connectFromField").getValue());
        assertEquals("targetNodeId", upstream.getString("connectToField").getValue());
        assertEquals("upstream", upstream.getString("as").getValue());
        assertTrue(stages.get(0).getDocument("$match").toJson().contains("organisationId"));
    }

    @Test
    @DisplayName("DOWNSTREAM only runs the downstream pass")
    void downstreamOnly() {
        when(aggregate.first()).thenReturn(Mono.just(new Document("downstream", List.of(edge(start, UUID.randomUUID(), 0L)))));

        StepVerifier.create(adapter.traverse(organisationId, start, 1, TraversalDirection.DOWNSTREAM))
                .assertNext(result -> {
                    assertEquals(1, result.downstream().size());
                    assertTrue(result.upstream().isEmpty());
                })
                .verifyComplete();

        List<BsonDocument> stages = capturedStages();
        assertEquals(2, stages.size());
        assertEquals("downstream", stages.get(1).getDocument("$graphLookup").getString("as").getValue());
    }

    @Test
    @DisplayName("UPSTREAM only runs the upstream pass")
    void upstreamOnly() {
        when(aggregate.first()).thenReturn(Mono.just(new Document("upstream", List.of(edge(UUID.randomUUID(), start, 0L)))));

        StepVerifier.create(adapter.traverse(organisationId, start, 5, TraversalDirection.UPSTREAM))
                .assertNext(result -> {
                    assertTrue(result.downstream().isEmpty());
                    assertEquals(1, result.upstream().size());
                })
                .verifyComplete();

        List<BsonDocument> stages = capturedStages();
        assertEquals(2, stages.size());
        assertEquals("upstream", stages.get(1).getDocument("$graphLookup").getString("as").getValue());
    }

    @Test
    @DisplayName("a start node that does not match the tenant yields an empty result")
    void noStartDocument() {
        when(aggregate.first()).thenReturn(Mono.empty());

        StepVerifier.create(adapter.traverse(organisationId, start, 3, TraversalDirection.BOTH))
                .expectNext(new TraversalResult(List.of(), List.of()))
                .verifyComplete();
    }
}
