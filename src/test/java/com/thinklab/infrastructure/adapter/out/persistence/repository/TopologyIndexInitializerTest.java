package com.thinklab.infrastructure.adapter.out.persistence.repository;

import com.mongodb.MongoTimeoutException;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.reactivestreams.client.FindPublisher;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;
import com.thinklab.infrastructure.adapter.out.persistence.entity.NodeDocument;
import io.micronaut.context.event.StartupEvent;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class TopologyIndexInitializerTest {

    private final StartupEvent startup = mock(StartupEvent.class);

    @Test
    @DisplayName("startup creates the two unique indexes and the two traversal indexes with the exact key order")
    void createsAllIndexes() {
        MongoClient client = mock(MongoClient.class);
        MongoDatabase database = mock(MongoDatabase.class);
        MongoCollection<Document> nodes = mock(MongoCollection.class);
        MongoCollection<Document> edges = mock(MongoCollection.class);
        when(client.getDatabase("tenant_topology")).thenReturn(database);
        when(database.getCollection("nodes")).thenReturn(nodes);
        when(database.getCollection("edges")).thenReturn(edges);
        when(nodes.createIndex(any(), any(IndexOptions.class))).thenReturn(Mono.just("ok"));
        when(edges.createIndex(any(), any(IndexOptions.class))).thenReturn(Mono.just("ok"));

        new TopologyIndexInitializer(client, "mongodb://mongo:27017/tenant_topology").onApplicationEvent(startup);

        ArgumentCaptor<Bson> nodeKeys = ArgumentCaptor.forClass(Bson.class);
        ArgumentCaptor<IndexOptions> nodeOptions = ArgumentCaptor.forClass(IndexOptions.class);
        verify(nodes).createIndex(nodeKeys.capture(), nodeOptions.capture());
        assertEquals(new Document("organisationId", 1).append("nodeType", 1).append("externalId", 1), nodeKeys.getValue());
        assertTrue(nodeOptions.getValue().isUnique());
        assertEquals(TopologyIndexInitializer.NODE_KEY_INDEX, nodeOptions.getValue().getName());

        ArgumentCaptor<Bson> edgeKeys = ArgumentCaptor.forClass(Bson.class);
        ArgumentCaptor<IndexOptions> edgeOptions = ArgumentCaptor.forClass(IndexOptions.class);
        verify(edges, times(3)).createIndex(edgeKeys.capture(), edgeOptions.capture());
        List<Bson> keys = edgeKeys.getAllValues();
        List<IndexOptions> options = edgeOptions.getAllValues();
        assertEquals(new Document("organisationId", 1).append("relationshipType", 1).append("sourceNodeId", 1).append("targetNodeId", 1), keys.get(0));
        assertTrue(options.get(0).isUnique());
        assertEquals(new Document("organisationId", 1).append("status", 1).append("sourceNodeId", 1), keys.get(1));
        assertEquals(TopologyIndexInitializer.EDGE_SOURCE_INDEX, options.get(1).getName());
        assertEquals(new Document("organisationId", 1).append("status", 1).append("targetNodeId", 1), keys.get(2));
        assertEquals(TopologyIndexInitializer.EDGE_TARGET_INDEX, options.get(2).getName());
        assertEquals(false, options.get(1).isUnique());
    }

    @Test
    @DisplayName("fail-open: an unreachable server or a rejected index is logged, never propagated")
    void failOpen() {
        MongoClient client = mock(MongoClient.class);
        MongoDatabase database = mock(MongoDatabase.class);
        MongoCollection<Document> collection = mock(MongoCollection.class);
        when(client.getDatabase("topology_db")).thenReturn(database);
        when(database.getCollection(any())).thenReturn(collection);
        when(collection.createIndex(any(), any(IndexOptions.class)))
                .thenReturn(Mono.error(new MongoTimeoutException("no server")))
                .thenReturn(Mono.error(new IllegalStateException("E11000 existing duplicates")));
        TopologyIndexInitializer initializer = new TopologyIndexInitializer(client, "mongodb://mongo:27017/topology_db", Duration.ofSeconds(1));

        assertDoesNotThrow(() -> initializer.onApplicationEvent(startup));
    }

    @Test
    @DisplayName("collaborators, mongodb.uri and the startup event are null-checked")
    void guards() {
        MongoClient client = mock(MongoClient.class);
        assertThrows(NullPointerException.class, () -> new TopologyIndexInitializer(null, "mongodb://mongo:27017/a"));
        assertThrows(NullPointerException.class, () -> new TopologyIndexInitializer(client, null));
        assertThrows(NullPointerException.class, () -> new TopologyIndexInitializer(client, "mongodb://mongo:27017/a").onApplicationEvent(null));
    }

    @Test
    @DisplayName("every adapter reads the database named in mongodb.uri, falling back to the service default")
    void databaseResolution() {
        MongoClient client = mock(MongoClient.class);
        MongoDatabase database = mock(MongoDatabase.class);
        MongoCollection<NodeDocument> collection = mock(MongoCollection.class);
        FindPublisher<NodeDocument> find = mock(FindPublisher.class);
        when(client.getDatabase("thinklab_topology_graph_db")).thenReturn(database);
        when(database.getCollection("nodes", NodeDocument.class)).thenReturn(collection);
        when(collection.withCodecRegistry(any())).thenReturn(collection);
        when(collection.find(any(Bson.class))).thenReturn(find);
        when(find.first()).thenReturn(Mono.empty());

        new NodeMongoRepositoryAdapter(client, "mongodb://mongo:27017").findById(UUID.randomUUID()).block();

        verify(client).getDatabase("thinklab_topology_graph_db");
        assertThrows(NullPointerException.class, () -> new NodeMongoRepositoryAdapter(client, null));
    }
}
