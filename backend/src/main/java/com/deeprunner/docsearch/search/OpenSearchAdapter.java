package com.deeprunner.docsearch.search;

import com.deeprunner.docsearch.domain.dto.PageInfoDto;
import com.deeprunner.docsearch.domain.dto.SearchHitDto;
import com.deeprunner.docsearch.domain.dto.SearchResultDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.http.entity.ContentType;
import org.apache.http.nio.entity.NStringEntity;
import org.opensearch.client.Request;
import org.opensearch.client.Response;
import org.opensearch.client.ResponseException;
import org.opensearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

@Component
public class OpenSearchAdapter {

    private static final Logger log = LoggerFactory.getLogger(OpenSearchAdapter.class);

    public static final String INDEX_ALIAS = "documents-live";
    public static final String INITIAL_INDEX = "documents-v1";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Value("${docsearch.opensearch.shards:3}")
    private int shards;

    @Value("${docsearch.opensearch.replicas:0}")
    private int replicas;

    @Value("${docsearch.opensearch.refresh-interval:1s}")
    private String refreshInterval;

    public OpenSearchAdapter(RestClient restClient, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void initIndex() {
        try {
            ensureIndexAndAlias();
        } catch (Exception e) {
            log.warn("Could not initialize OpenSearch index during startup (will retry on operations): {}", e.getMessage());
        }
    }

    public synchronized void ensureIndexAndAlias() throws IOException {
        Request headRequest = new Request("HEAD", "/" + INDEX_ALIAS);
        try {
            Response response = restClient.performRequest(headRequest);
            if (response.getStatusLine().getStatusCode() == 200) {
                return;
            }
        } catch (ResponseException e) {
            if (e.getResponse().getStatusLine().getStatusCode() != 404) {
                throw e;
            }
        }

        // Check if initial index exists
        Request checkIdx = new Request("HEAD", "/" + INITIAL_INDEX);
        boolean indexExists = false;
        try {
            Response r = restClient.performRequest(checkIdx);
            indexExists = (r.getStatusLine().getStatusCode() == 200);
        } catch (ResponseException ignored) {
        }

        if (!indexExists) {
            String indexMapping = """
            {
              "settings": {
                "index": {
                  "number_of_shards": %d,
                  "number_of_replicas": %d,
                  "refresh_interval": "%s"
                }
              },
              "mappings": {
                "_routing": {
                  "required": true
                },
                "dynamic": "strict",
                "properties": {
                  "id": { "type": "keyword" },
                  "tenantId": { "type": "keyword" },
                  "externalId": { "type": "keyword" },
                  "title": { "type": "text", "analyzer": "standard" },
                  "content": { "type": "text", "analyzer": "standard", "term_vector": "with_positions_offsets" },
                  "author": {
                    "type": "text",
                    "fields": {
                      "keyword": { "type": "keyword", "ignore_above": 256 },
                      "text": { "type": "text" }
                    }
                  },
                  "tags": { "type": "keyword" },
                  "createdAt": { "type": "date" },
                  "updatedAt": { "type": "date" }
                }
              },
              "aliases": {
                "%s": {}
              }
            }
            """.formatted(shards, replicas, refreshInterval, INDEX_ALIAS);

            Request createReq = new Request("PUT", "/" + INITIAL_INDEX);
            createReq.setEntity(new NStringEntity(indexMapping, ContentType.APPLICATION_JSON));
            restClient.performRequest(createReq);
            log.info("Initialized OpenSearch index '{}' with alias '{}'", INITIAL_INDEX, INDEX_ALIAS);
        }
    }

    public boolean indexDocument(OpenSearchDocument doc) throws IOException {
        String compositeId = doc.tenantId() + ":" + doc.id();
        Request req = new Request("PUT", "/" + INDEX_ALIAS + "/_doc/" + compositeId);
        req.addParameter("routing", doc.tenantId());
        req.setEntity(new NStringEntity(objectMapper.writeValueAsString(doc), ContentType.APPLICATION_JSON));

        Response response = restClient.performRequest(req);
        int code = response.getStatusLine().getStatusCode();
        return code >= 200 && code < 300;
    }

    public boolean bulkIndexDocuments(List<OpenSearchDocument> docs) throws IOException {
        if (docs == null || docs.isEmpty()) {
            return true;
        }

        StringBuilder ndjson = new StringBuilder();
        for (OpenSearchDocument doc : docs) {
            String compositeId = doc.tenantId() + ":" + doc.id();
            ObjectNode actionMeta = objectMapper.createObjectNode();
            ObjectNode indexParams = actionMeta.putObject("index");
            indexParams.put("_index", INDEX_ALIAS);
            indexParams.put("_id", compositeId);
            indexParams.put("routing", doc.tenantId());

            ndjson.append(objectMapper.writeValueAsString(actionMeta)).append("\n");
            ndjson.append(objectMapper.writeValueAsString(doc)).append("\n");
        }

        Request req = new Request("POST", "/_bulk");
        req.setEntity(new NStringEntity(ndjson.toString(), ContentType.APPLICATION_JSON));

        Response response = restClient.performRequest(req);
        return response.getStatusLine().getStatusCode() == 200;
    }

    public boolean deleteDocument(String tenantId, UUID documentId) throws IOException {
        String compositeId = tenantId + ":" + documentId;
        Request req = new Request("DELETE", "/" + INDEX_ALIAS + "/_doc/" + compositeId);
        req.addParameter("routing", tenantId);

        try {
            Response response = restClient.performRequest(req);
            int code = response.getStatusLine().getStatusCode();
            return code >= 200 && code < 300;
        } catch (ResponseException e) {
            if (e.getResponse().getStatusLine().getStatusCode() == 404) {
                return true; // Already deleted
            }
            throw e;
        }
    }

    public SearchResultDto executeSearch(String tenantId,
                                         String rawQuery,
                                         ObjectNode queryDsl,
                                         int from,
                                         int size) throws IOException {
        Request req = new Request("POST", "/" + INDEX_ALIAS + "/_search");
        req.addParameter("routing", tenantId);
        req.setEntity(new NStringEntity(objectMapper.writeValueAsString(queryDsl), ContentType.APPLICATION_JSON));

        long startTime = System.currentTimeMillis();
        Response response = restClient.performRequest(req);
        long tookMs = System.currentTimeMillis() - startTime;

        String body = new String(response.getEntity().getContent().readAllBytes(), StandardCharsets.UTF_8);
        JsonNode root = objectMapper.readTree(body);

        JsonNode hitsNode = root.path("hits");
        long totalHits = hitsNode.path("total").path("value").asLong(0);
        String relation = hitsNode.path("total").path("relation").asText("eq");
        boolean isLowerBound = "gte".equalsIgnoreCase(relation);

        List<SearchHitDto> hitDtos = new ArrayList<>();
        JsonNode hitsArray = hitsNode.path("hits");
        if (hitsArray.isArray()) {
            for (JsonNode h : hitsArray) {
                double score = h.path("_score").asDouble(0.0);
                JsonNode source = h.path("_source");
                UUID id = UUID.fromString(source.path("id").asText());
                String externalId = source.path("externalId").asText(null);
                String title = source.path("title").asText("");
                String author = source.path("author").asText(null);
                Instant createdAt = Instant.parse(source.path("createdAt").asText(Instant.now().toString()));

                List<String> tags = new ArrayList<>();
                JsonNode tagsNode = source.path("tags");
                if (tagsNode.isArray()) {
                    for (JsonNode t : tagsNode) {
                        tags.add(t.asText());
                    }
                }

                // Highlight snippet
                String snippet = "";
                JsonNode highlight = h.path("highlight");
                if (highlight.has("content")) {
                    JsonNode fragments = highlight.path("content");
                    if (fragments.isArray() && fragments.size() > 0) {
                        snippet = fragments.get(0).asText();
                    }
                }
                if (snippet.isBlank() && highlight.has("title")) {
                    snippet = highlight.path("title").get(0).asText();
                }

                hitDtos.add(new SearchHitDto(id, externalId, title, snippet, score, author, tags, createdAt));
            }
        }

        // Parse facet aggregations
        Map<String, Map<String, Long>> facets = new HashMap<>();
        JsonNode aggsNode = root.path("aggregations");
        if (aggsNode.has("tags")) {
            Map<String, Long> tagCounts = new LinkedHashMap<>();
            JsonNode buckets = aggsNode.path("tags").path("buckets");
            if (buckets.isArray()) {
                for (JsonNode b : buckets) {
                    tagCounts.put(b.path("key").asText(), b.path("doc_count").asLong());
                }
            }
            facets.put("tags", tagCounts);
        }

        PageInfoDto pageInfo = new PageInfoDto(from, size, totalHits, isLowerBound);
        return new SearchResultDto(rawQuery, tenantId, hitDtos, pageInfo, tookMs, false, facets);
    }

    public boolean isHealthy() {
        try {
            Request req = new Request("GET", "/_cluster/health");
            Response res = restClient.performRequest(req);
            if (res.getStatusLine().getStatusCode() == 200) {
                String body = new String(res.getEntity().getContent().readAllBytes(), StandardCharsets.UTF_8);
                JsonNode json = objectMapper.readTree(body);
                String status = json.path("status").asText("");
                // Yellow is accepted locally (single node, 0 replicas)
                return "green".equalsIgnoreCase(status) || "yellow".equalsIgnoreCase(status);
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }
}
