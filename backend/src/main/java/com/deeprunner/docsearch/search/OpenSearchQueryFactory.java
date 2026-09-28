package com.deeprunner.docsearch.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Builds OpenSearch DSL queries strictly enforcing tenant isolation.
 * Throws IllegalStateException if tenantId is missing or empty.
 */
@Component
public class OpenSearchQueryFactory {

    private final ObjectMapper mapper;

    public OpenSearchQueryFactory(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public ObjectNode buildSearchQuery(String tenantId,
                                       String rawQuery,
                                       int from,
                                       int size,
                                       List<String> tags,
                                       boolean fuzzy,
                                       boolean highlight) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalStateException("Security violation: tenantId cannot be null or blank in query construction");
        }

        ObjectNode root = mapper.createObjectNode();
        root.put("from", Math.max(0, from));
        root.put("size", Math.min(100, Math.max(1, size)));
        root.put("timeout", "400ms");
        root.put("track_total_hits", 10000);

        // _source filtering: exclude heavy content from response
        ArrayNode sourceIncludes = root.putArray("_source");
        sourceIncludes.add("id");
        sourceIncludes.add("tenantId");
        sourceIncludes.add("externalId");
        sourceIncludes.add("title");
        sourceIncludes.add("author");
        sourceIncludes.add("tags");
        sourceIncludes.add("createdAt");

        ObjectNode queryNode = root.putObject("query");
        ObjectNode boolNode = queryNode.putObject("bool");

        // Mandatory tenant filter (cached and non-scoring)
        ArrayNode filterArray = boolNode.putArray("filter");
        ObjectNode tenantTerm = filterArray.addObject();
        tenantTerm.putObject("term").put("tenantId", tenantId.trim().toLowerCase());

        // Optional tags filter
        if (tags != null && !tags.isEmpty()) {
            for (String tag : tags) {
                if (tag != null && !tag.isBlank()) {
                    ObjectNode tagTerm = filterArray.addObject();
                    tagTerm.putObject("term").put("tags", tag.trim().toLowerCase());
                }
            }
        }

        ArrayNode mustArray = boolNode.putArray("must");
        if (rawQuery == null || rawQuery.trim().isEmpty() || "*".equals(rawQuery.trim())) {
            mustArray.addObject().putObject("match_all");
        } else {
            String queryStr = rawQuery.trim();
            // Best fields multi match
            ObjectNode multiMatchObj = mustArray.addObject().putObject("multi_match");
            multiMatchObj.put("query", queryStr);
            ArrayNode fields = multiMatchObj.putArray("fields");
            fields.add("title^3");
            fields.add("tags^2");
            fields.add("author.text^1.5");
            fields.add("content^1");
            multiMatchObj.put("type", "best_fields");
            multiMatchObj.put("minimum_should_match", "2<70%");
            if (fuzzy) {
                multiMatchObj.put("fuzziness", "AUTO");
            }

            // Phrase match boost in should clause
            ArrayNode shouldArray = boolNode.putArray("should");
            ObjectNode phraseMatch = shouldArray.addObject().putObject("multi_match");
            phraseMatch.put("query", queryStr);
            phraseMatch.put("type", "phrase");
            phraseMatch.put("slop", 2);
            phraseMatch.put("boost", 2.0);
        }

        // Highlighting configuration
        if (highlight) {
            ObjectNode highlightNode = root.putObject("highlight");
            ArrayNode preTags = highlightNode.putArray("pre_tags");
            preTags.add("<em>");
            ArrayNode postTags = highlightNode.putArray("post_tags");
            postTags.add("</em>");

            ObjectNode highlightFields = highlightNode.putObject("fields");
            ObjectNode contentHighlight = highlightFields.putObject("content");
            contentHighlight.put("fragment_size", 160);
            contentHighlight.put("number_of_fragments", 3);

            ObjectNode titleHighlight = highlightFields.putObject("title");
            titleHighlight.put("number_of_fragments", 0);
        }

        // Facets/aggregations for tags
        ObjectNode aggsNode = root.putObject("aggs");
        ObjectNode tagsAgg = aggsNode.putObject("tags").putObject("terms");
        tagsAgg.put("field", "tags");
        tagsAgg.put("size", 10);

        return root;
    }
}
