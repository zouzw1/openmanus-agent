package com.openmanus.saa.rag.elasticsearch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.openmanus.saa.config.RagProperties;
import com.openmanus.saa.rag.api.RagDocumentStore;
import com.openmanus.saa.rag.model.KnowledgeChunkRecord;
import com.openmanus.saa.rag.model.KnowledgeScope;
import com.openmanus.saa.rag.model.RetrievalHit;
import com.openmanus.saa.rag.model.RetrievalRequest;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ElasticsearchRagDocumentStore implements RagDocumentStore {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final RagProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Set<String> ensuredIndexes = ConcurrentHashMap.newKeySet();

    public ElasticsearchRagDocumentStore(RagProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(REQUEST_TIMEOUT)
                .build();
    }

    public RagProperties properties() {
        return properties;
    }

    @Override
    public void upsertChunks(List<KnowledgeChunkRecord> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }

        Map<String, List<KnowledgeChunkRecord>> chunksByIndex = new LinkedHashMap<>();
        for (KnowledgeChunkRecord chunk : chunks) {
            String indexName = indexName(chunk.knowledgeBaseId());
            ensureIndex(indexName);
            chunksByIndex.computeIfAbsent(indexName, ignored -> new ArrayList<>()).add(chunk);
        }

        chunksByIndex.forEach((indexName, records) -> executeBulkUpsert(indexName, records));
    }

    @Override
    public List<RetrievalHit> search(RetrievalRequest request, float[] queryVector) {
        if (request == null || request.query() == null || request.query().isBlank()) {
            return List.of();
        }

        String indexPath = resolveSearchIndexPath(request.scope());
        if (indexPath == null) {
            return List.of();
        }

        ObjectNode root = objectMapper.createObjectNode();
        root.put("size", Math.max(1, request.topK()));
        ArrayNode source = root.putArray("_source");
        source.add("tenantId");
        source.add("knowledgeBaseId");
        source.add("documentId");
        source.add("chunkId");
        source.add("title");
        source.add("source");
        source.add("sourceType");
        source.add("chunkIndex");
        source.add("language");
        source.add("category");
        source.add("tags");
        source.add("text");
        source.add("metadata");

        root.set("query", buildSearchQuery(request, queryVector));

        JsonNode response = sendJson("POST", "/" + indexPath + "/_search", root);
        JsonNode hitsNode = response.path("hits").path("hits");
        if (!hitsNode.isArray()) {
            return List.of();
        }

        List<RetrievalHit> hits = new ArrayList<>();
        for (JsonNode hitNode : hitsNode) {
            JsonNode sourceNode = hitNode.path("_source");
            hits.add(new RetrievalHit(
                    sourceNode.path("knowledgeBaseId").asText(null),
                    sourceNode.path("documentId").asText(null),
                    sourceNode.path("chunkId").asText(null),
                    sourceNode.path("text").asText(""),
                    hitNode.path("_score").asDouble(0.0d),
                    buildRetrievalMetadata(sourceNode)
            ));
        }
        return hits;
    }

    @Override
    public void deleteDocument(String knowledgeBaseId, String documentId) {
        if (knowledgeBaseId == null || knowledgeBaseId.isBlank() || documentId == null || documentId.isBlank()) {
            return;
        }
        String indexName = indexName(knowledgeBaseId);
        ensureIndex(indexName);

        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode query = root.putObject("query");
        ObjectNode boolNode = query.putObject("bool");
        ArrayNode filterArray = boolNode.putArray("filter");
        ObjectNode termDocument = filterArray.addObject().putObject("term");
        termDocument.put("documentId", documentId);

        sendJson("POST", "/" + indexName + "/_delete_by_query", root);
    }

    private String resolveSearchIndexPath(KnowledgeScope scope) {
        List<String> knowledgeBaseIds = scope == null ? List.of() : scope.knowledgeBaseIds();
        if (knowledgeBaseIds == null || knowledgeBaseIds.isEmpty()) {
            return null;
        }
        List<String> indexNames = knowledgeBaseIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .map(this::indexName)
                .distinct()
                .toList();
        indexNames.forEach(this::ensureIndex);
        return String.join(",", indexNames);
    }

    private JsonNode buildSearchQuery(RetrievalRequest request, float[] queryVector) {
        ObjectNode scriptScore = objectMapper.createObjectNode();
        ObjectNode queryNode = scriptScore.putObject("query");
        ObjectNode boolNode = queryNode.putObject("bool");

        if (request.hybrid()) {
            ArrayNode shouldArray = boolNode.putArray("should");
            ObjectNode multiMatchNode = shouldArray.addObject().putObject("multi_match");
            multiMatchNode.put("query", request.query());
            ArrayNode fieldsNode = multiMatchNode.putArray("fields");
            fieldsNode.add("title^2");
            fieldsNode.add("text");
            boolNode.put("minimum_should_match", 0);
        } else {
            boolNode.putArray("must").addObject().putObject("match_all");
        }

        ArrayNode filterArray = boolNode.putArray("filter");
        addFilters(filterArray, request);

        ObjectNode scriptNode = scriptScore.putObject("script");
        scriptNode.put(
                "source",
                request.hybrid()
                        ? "cosineSimilarity(params.queryVector, 'textVector') + 1.0 + _score"
                        : "cosineSimilarity(params.queryVector, 'textVector') + 1.0"
        );
        ArrayNode queryVectorNode = scriptNode.putObject("params").putArray("queryVector");
        if (queryVector != null) {
            for (float value : queryVector) {
                queryVectorNode.add(value);
            }
        }

        return objectMapper.createObjectNode().set("script_score", scriptScore);
    }

    private void addFilters(ArrayNode filterArray, RetrievalRequest request) {
        Map<String, Object> mergedFilters = new LinkedHashMap<>();
        if (request.scope() != null && request.scope().filters() != null) {
            mergedFilters.putAll(request.scope().filters());
        }
        if (request.filters() != null) {
            mergedFilters.putAll(request.filters());
        }

        for (Map.Entry<String, Object> entry : mergedFilters.entrySet()) {
            if (entry.getValue() == null) {
                continue;
            }
            String fieldName = normalizeFilterField(entry.getKey());
            Object value = entry.getValue();
            if (value instanceof Collection<?> collection && !collection.isEmpty()) {
                ObjectNode termsNode = filterArray.addObject().putObject("terms");
                ArrayNode valuesNode = termsNode.putArray(fieldName);
                collection.forEach(item -> valuesNode.add(String.valueOf(item)));
            } else {
                ObjectNode termNode = filterArray.addObject().putObject("term");
                if (value instanceof Number number) {
                    termNode.put(fieldName, number.doubleValue());
                } else if (value instanceof Boolean bool) {
                    termNode.put(fieldName, bool);
                } else {
                    termNode.put(fieldName, String.valueOf(value));
                }
            }
        }
    }

    private String normalizeFilterField(String rawField) {
        if (rawField == null || rawField.isBlank()) {
            return "metadata.value";
        }
        if (rawField.startsWith("metadata.") || isTopLevelFilterField(rawField)) {
            return rawField;
        }
        return "metadata." + rawField;
    }

    private void executeBulkUpsert(String indexName, List<KnowledgeChunkRecord> records) {
        StringBuilder payload = new StringBuilder();
        for (KnowledgeChunkRecord record : records) {
            payload.append("{\"index\":{\"_id\":\"")
                    .append(escapeJson(record.chunkId()))
                    .append("\"}}\n");
            payload.append(toDocumentJson(record)).append('\n');
        }
        sendBulk("/" + indexName + "/_bulk", payload.toString());
    }

    private String toDocumentJson(KnowledgeChunkRecord record) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("tenantId", record.tenantId());
        root.put("knowledgeBaseId", record.knowledgeBaseId());
        root.put("documentId", record.documentId());
        root.put("chunkId", record.chunkId());
        root.put("title", record.title());
        root.put("titleKeyword", record.titleKeyword());
        root.put("source", record.source());
        root.put("sourceType", record.sourceType());
        root.put("text", record.text());
        putInteger(root, "chunkIndex", record.chunkIndex());
        putInteger(root, "chunkStart", record.chunkStart());
        putInteger(root, "chunkEnd", record.chunkEnd());
        putInteger(root, "chunkLength", record.chunkLength());
        appendStringArray(root.putArray("tags"), record.tags());
        putString(root, "language", record.language());
        putString(root, "category", record.category());
        putString(root, "version", record.version());
        putString(root, "author", record.author());
        putString(root, "embeddingModel", record.embeddingModel());
        putInteger(root, "embeddingDimensions", record.embeddingDimensions());
        putString(root, "embeddingVersion", record.embeddingVersion());
        putString(root, "createdAt", record.createdAt());
        putString(root, "updatedAt", record.updatedAt());
        putString(root, "ingestedAt", record.ingestedAt());
        putString(root, "ingestBatchId", record.ingestBatchId());
        if (record.deleted() != null) {
            root.put("deleted", record.deleted());
        }
        ArrayNode vectorNode = root.putArray("textVector");
        if (record.vector() != null) {
            for (float value : record.vector()) {
                vectorNode.add(value);
            }
        }
        root.set("metadata", objectMapper.valueToTree(record.metadata() == null ? Map.of() : record.metadata()));
        try {
            return objectMapper.writeValueAsString(root);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to serialize knowledge chunk record", ex);
        }
    }

    private void ensureIndex(String indexName) {
        if (!ensuredIndexes.add(indexName)) {
            return;
        }
        try {
            HttpResponse<String> existsResponse = send("HEAD", "/" + indexName, null, "application/json");
            if (existsResponse.statusCode() == 200) {
                ensureMapping(indexName);
                return;
            }
            ObjectNode root = objectMapper.createObjectNode();
            ObjectNode mappings = root.putObject("mappings");
            mappings.set("properties", buildPropertiesNode());
            sendJson("PUT", "/" + indexName, root);
            ensureMapping(indexName);
        } catch (RuntimeException ex) {
            ensuredIndexes.remove(indexName);
            throw ex;
        }
    }

    private void ensureMapping(String indexName) {
        ObjectNode root = objectMapper.createObjectNode();
        root.set("properties", buildPropertiesNode());
        sendJson("PUT", "/" + indexName + "/_mapping", root);
    }

    private ObjectNode buildPropertiesNode() {
        ObjectNode propertiesNode = objectMapper.createObjectNode();
        propertiesNode.putObject("tenantId").put("type", "keyword");
        propertiesNode.putObject("knowledgeBaseId").put("type", "keyword");
        propertiesNode.putObject("documentId").put("type", "keyword");
        propertiesNode.putObject("chunkId").put("type", "keyword");
        propertiesNode.putObject("title").put("type", "text");
        propertiesNode.putObject("titleKeyword").put("type", "keyword");
        propertiesNode.putObject("source").put("type", "keyword");
        propertiesNode.putObject("sourceType").put("type", "keyword");
        propertiesNode.putObject("text").put("type", "text");
        ObjectNode vectorNode = propertiesNode.putObject("textVector");
        vectorNode.put("type", "dense_vector");
        vectorNode.put("dims", properties.getElasticsearch().getVectorDimensions());
        propertiesNode.putObject("chunkIndex").put("type", "integer");
        propertiesNode.putObject("chunkStart").put("type", "integer");
        propertiesNode.putObject("chunkEnd").put("type", "integer");
        propertiesNode.putObject("chunkLength").put("type", "integer");
        propertiesNode.putObject("tags").put("type", "keyword");
        propertiesNode.putObject("language").put("type", "keyword");
        propertiesNode.putObject("category").put("type", "keyword");
        propertiesNode.putObject("version").put("type", "keyword");
        propertiesNode.putObject("author").put("type", "keyword");
        propertiesNode.putObject("embeddingModel").put("type", "keyword");
        propertiesNode.putObject("embeddingDimensions").put("type", "integer");
        propertiesNode.putObject("embeddingVersion").put("type", "keyword");
        propertiesNode.putObject("createdAt").put("type", "date");
        propertiesNode.putObject("updatedAt").put("type", "date");
        propertiesNode.putObject("ingestedAt").put("type", "date");
        propertiesNode.putObject("ingestBatchId").put("type", "keyword");
        propertiesNode.putObject("deleted").put("type", "boolean");
        propertiesNode.putObject("metadata").put("type", "object");
        return propertiesNode;
    }

    private boolean isTopLevelFilterField(String fieldName) {
        return switch (fieldName) {
            case "tenantId", "knowledgeBaseId", "documentId", "chunkId", "titleKeyword", "source", "sourceType",
                    "chunkIndex", "chunkStart", "chunkEnd", "chunkLength", "language", "category", "version",
                    "author", "embeddingModel", "embeddingDimensions", "embeddingVersion", "createdAt", "updatedAt",
                    "ingestedAt", "ingestBatchId", "deleted", "tags" -> true;
            default -> false;
        };
    }

    private Map<String, Object> buildRetrievalMetadata(JsonNode sourceNode) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        Map<String, Object> storedMetadata = objectMapper.convertValue(sourceNode.path("metadata"), Map.class);
        if (storedMetadata != null) {
            metadata.putAll(storedMetadata);
        }
        putIfPresent(metadata, "tenantId", sourceNode.path("tenantId").asText(null));
        putIfPresent(metadata, "title", sourceNode.path("title").asText(null));
        putIfPresent(metadata, "source", sourceNode.path("source").asText(null));
        putIfPresent(metadata, "sourceType", sourceNode.path("sourceType").asText(null));
        putIfPresent(metadata, "chunkIndex", sourceNode.path("chunkIndex").isMissingNode() ? null : sourceNode.path("chunkIndex").asInt());
        putIfPresent(metadata, "language", sourceNode.path("language").asText(null));
        putIfPresent(metadata, "category", sourceNode.path("category").asText(null));
        if (sourceNode.path("tags").isArray()) {
            List<String> tags = new ArrayList<>();
            sourceNode.path("tags").forEach(tag -> tags.add(tag.asText()));
            if (!tags.isEmpty()) {
                metadata.put("tags", tags);
            }
        }
        return metadata;
    }

    private void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof String stringValue && stringValue.isBlank()) {
            return;
        }
        target.put(key, value);
    }

    private void putInteger(ObjectNode root, String fieldName, Integer value) {
        if (value != null) {
            root.put(fieldName, value);
        }
    }

    private void putString(ObjectNode root, String fieldName, String value) {
        if (value != null && !value.isBlank()) {
            root.put(fieldName, value);
        }
    }

    private void appendStringArray(ArrayNode arrayNode, List<String> values) {
        if (values == null) {
            return;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                arrayNode.add(value);
            }
        }
    }

    private JsonNode sendJson(String method, String path, JsonNode body) {
        try {
            String payload = body == null ? null : objectMapper.writeValueAsString(body);
            HttpResponse<String> response = send(method, path, payload, "application/json");
            ensureSuccess(response, method, path);
            return response.body() == null || response.body().isBlank()
                    ? objectMapper.createObjectNode()
                    : objectMapper.readTree(response.body());
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to parse Elasticsearch response for " + path, ex);
        }
    }

    private void sendBulk(String path, String payload) {
        HttpResponse<String> response = send("POST", path, payload, "application/x-ndjson");
        ensureSuccess(response, "POST", path);
        try {
            JsonNode body = response.body() == null || response.body().isBlank()
                    ? objectMapper.createObjectNode()
                    : objectMapper.readTree(response.body());
            if (body.path("errors").asBoolean(false)) {
                throw new IllegalStateException("Elasticsearch bulk request reported item errors: " + response.body());
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to parse Elasticsearch bulk response", ex);
        }
    }

    private HttpResponse<String> send(String method, String path, String body, String contentType) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUri() + path))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Accept", "application/json");

            if (contentType != null && body != null) {
                builder.header("Content-Type", contentType);
            }
            String authHeader = authorizationHeader();
            if (authHeader != null) {
                builder.header("Authorization", authHeader);
            }

            switch (method) {
                case "HEAD" -> builder.method("HEAD", HttpRequest.BodyPublishers.noBody());
                case "PUT" -> builder.PUT(HttpRequest.BodyPublishers.ofString(body == null ? "" : body));
                case "POST" -> builder.POST(HttpRequest.BodyPublishers.ofString(body == null ? "" : body));
                case "DELETE" -> builder.DELETE();
                default -> throw new IllegalArgumentException("Unsupported HTTP method: " + method);
            }
            return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Failed to call Elasticsearch " + method + " " + path, ex);
        }
    }

    private void ensureSuccess(HttpResponse<String> response, String method, String path) {
        int statusCode = response.statusCode();
        if (statusCode >= 200 && statusCode < 300) {
            return;
        }
        throw new IllegalStateException("Elasticsearch request failed: " + method + " " + path
                + " -> HTTP " + statusCode + " | " + response.body());
    }

    private String baseUri() {
        String[] uris = properties.getElasticsearch().getUris();
        String raw = (uris != null && uris.length > 0 ? uris[0] : "http://localhost:9200").trim();
        return raw.endsWith("/") ? raw.substring(0, raw.length() - 1) : raw;
    }

    private String authorizationHeader() {
        String username = properties.getElasticsearch().getUsername();
        if (username == null || username.isBlank()) {
            return null;
        }
        String password = properties.getElasticsearch().getPassword();
        String token = Base64.getEncoder().encodeToString((username + ":" + (password == null ? "" : password))
                .getBytes(StandardCharsets.UTF_8));
        return "Basic " + token;
    }

    private String indexName(String knowledgeBaseId) {
        String normalized = knowledgeBaseId == null || knowledgeBaseId.isBlank() ? "default" : knowledgeBaseId;
        String sanitized = normalized.toLowerCase()
                .replaceAll("[^a-z0-9_-]+", "-")
                .replaceAll("-{2,}", "-")
                .replaceAll("^-|-$", "");
        if (sanitized.isBlank()) {
            sanitized = "default";
        }
        String prefix = properties.getElasticsearch().getIndexPrefix();
        String candidate = (prefix == null || prefix.isBlank() ? "openmanus_kb" : prefix) + "_" + sanitized;
        return candidate.length() > 255 ? candidate.substring(0, 255) : candidate;
    }

    private String escapeJson(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
