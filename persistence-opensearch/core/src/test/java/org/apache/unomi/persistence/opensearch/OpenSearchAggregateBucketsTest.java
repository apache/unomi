/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.unomi.persistence.opensearch;

import org.opensearch.client.opensearch._types.aggregations.Aggregate;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.json.JsonpDeserializer;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.apache.unomi.api.query.IpRange;
import org.apache.unomi.persistence.spi.aggregate.BaseAggregate;
import org.apache.unomi.persistence.spi.aggregate.IpRangeAggregate;
import org.apache.unomi.persistence.spi.aggregate.TermsAggregate;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reads aggregate buckets from OpenSearch response JSON, parsed by the client as it would be for a real search.
 */
public class OpenSearchAggregateBucketsTest {

    private static final BaseAggregate TERMS = new TermsAggregate("properties.field");

    @Test
    public void readsStringTerms() {
        Map<String, Long> results = read("sterms", "{\"doc_count_error_upper_bound\":0,\"sum_other_doc_count\":0,"
                + "\"buckets\":[{\"key\":\"Alice\",\"doc_count\":2},{\"key\":\"Bob\",\"doc_count\":1}]}", TERMS, 3);
        assertEquals(Long.valueOf(2L), results.get("Alice"));
        assertEquals(Long.valueOf(1L), results.get("Bob"));
    }

    @Test
    public void readsLongTermsWithoutKeyAsString() {
        Map<String, Long> results = read("lterms", "{\"doc_count_error_upper_bound\":0,\"sum_other_doc_count\":0,"
                + "\"buckets\":[{\"key\":30,\"doc_count\":2},{\"key\":70,\"doc_count\":1}]}", TERMS, 3);
        assertEquals(Long.valueOf(2L), results.get("30"));
        assertEquals(Long.valueOf(1L), results.get("70"));
        assertEquals(2, results.size());
    }

    @Test
    public void readsDoubleTerms() {
        Map<String, Long> results = read("dterms", "{\"doc_count_error_upper_bound\":0,\"sum_other_doc_count\":0,"
                + "\"buckets\":[{\"key\":2.5,\"doc_count\":4}]}", TERMS, 4);
        assertEquals(Long.valueOf(4L), results.get("2.5"));
    }

    @Test
    public void readsDateHistogramWithFormattedKeys() {
        Map<String, Long> results = read("date_histogram", "{\"buckets\":["
                + "{\"key_as_string\":\"2020-01-01\",\"key\":1577836800000,\"doc_count\":3},"
                + "{\"key_as_string\":\"2020-01-02\",\"key\":1577923200000,\"doc_count\":0}]}", TERMS, 3);
        assertEquals(Long.valueOf(3L), results.get("2020-01-01"));
        assertEquals(Long.valueOf(0L), results.get("2020-01-02"));
    }

    @Test
    public void readsNumericRanges() {
        Map<String, Long> results = read("range", "{\"buckets\":["
                + "{\"key\":\"young\",\"to\":18.0,\"doc_count\":1},"
                + "{\"key\":\"adult\",\"from\":18.0,\"to\":65.0,\"doc_count\":2},"
                + "{\"from\":65.0,\"doc_count\":1}]}", TERMS, 4);
        assertEquals(Long.valueOf(1L), results.get("young"));
        assertEquals(Long.valueOf(2L), results.get("adult"));
        assertEquals(Long.valueOf(1L), results.get("65.0-*"));
    }

    @Test
    public void readsDateRanges() {
        Map<String, Long> results = read("date_range", "{\"buckets\":["
                + "{\"key\":\"before\",\"to\":1577923200000,\"to_as_string\":\"2020-01-02\",\"doc_count\":3},"
                + "{\"from\":1577923200000,\"from_as_string\":\"2020-01-02\",\"doc_count\":1}]}", TERMS, 4);
        assertEquals(Long.valueOf(3L), results.get("before"));
        assertEquals(Long.valueOf(1L), results.get("2020-01-02-*"));
    }

    @Test
    public void readsIpRangesUnderTheirRangeKeys() {
        IpRangeAggregate aggregate = new IpRangeAggregate("properties.ip",
                Arrays.asList(ipRange("lan", "10.0.0.0", "10.0.1.0"), ipRange("loopback", "0:0:0:0:0:0:0:1", null)));
        Map<String, Long> results = read("ip_range", "{\"buckets\":["
                + "{\"key\":\"10.0.0.0-10.0.1.0\",\"from\":\"10.0.0.0\",\"to\":\"10.0.1.0\",\"doc_count\":2},"
                + "{\"key\":\"::1-*\",\"from\":\"::1\",\"doc_count\":1}]}", aggregate, 3);
        assertEquals(Long.valueOf(2L), results.get("lan"));
        assertEquals(Long.valueOf(1L), results.get("loopback"));
        assertEquals(2, results.size());
    }

    @Test
    public void leavesOutUnreadBucketTypes() {
        Map<String, Long> results = read("avg", "{\"value\":1.5}", TERMS, 0);
        assertTrue(results.isEmpty());
    }

    @Test
    public void bucketsNamedLikeResultCountsDoNotChangeThem() {
        Map<String, Long> results = new LinkedHashMap<>();
        results.put("_all", 10L);
        long total = OpenSearchPersistenceServiceImpl.addBucketCounts(parse("sterms",
                "{\"doc_count_error_upper_bound\":0,\"sum_other_doc_count\":0,"
                        + "\"buckets\":[{\"key\":\"_all\",\"doc_count\":3},{\"key\":\"x\",\"doc_count\":1}]}"), TERMS, results);
        assertEquals(4L, total);
        assertEquals(Long.valueOf(10L), results.get("_all"));
        assertEquals(Long.valueOf(1L), results.get("x"));
    }

    private static Map<String, Long> read(String type, String body, BaseAggregate aggregate, long expectedTotal) {
        Map<String, Long> results = new LinkedHashMap<>();
        assertEquals(expectedTotal, OpenSearchPersistenceServiceImpl.addBucketCounts(parse(type, body), aggregate, results));
        return results;
    }

    private static Aggregate parse(String type, String body) {
        String json = "{\"took\":1,\"timed_out\":false,"
                + "\"_shards\":{\"total\":1,\"successful\":1,\"skipped\":0,\"failed\":0},"
                + "\"hits\":{\"total\":{\"value\":0,\"relation\":\"eq\"},\"hits\":[]},"
                + "\"aggregations\":{\"" + type + "#buckets\":" + body + "}}";
        JsonpMapper mapper = new JacksonJsonpMapper();
        SearchResponse<Void> response = SearchResponse.createSearchResponseDeserializer(JsonpDeserializer.voidDeserializer())
                .deserialize(mapper.jsonProvider().createParser(new StringReader(json)), mapper);
        return response.aggregations().get("buckets");
    }

    private static IpRange ipRange(String key, String from, String to) {
        IpRange range = new IpRange();
        range.setKey(key);
        range.setFrom(from);
        range.setTo(to);
        return range;
    }
}
