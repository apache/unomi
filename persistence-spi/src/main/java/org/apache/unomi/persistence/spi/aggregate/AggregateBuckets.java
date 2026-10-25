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
package org.apache.unomi.persistence.spi.aggregate;

import org.apache.unomi.api.query.IpRange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Turns the buckets a search engine returns for an aggregate into the keys and counts of
 * {@link org.apache.unomi.persistence.spi.PersistenceService} aggregate results, so that every persistence
 * implementation reports buckets the same way.
 * <p>
 * Support code for persistence implementations, not intended for other callers.
 */
public final class AggregateBuckets {

    private static final Logger LOGGER = LoggerFactory.getLogger(AggregateBuckets.class);

    /**
     * Keys that aggregate results use for their own counts rather than for buckets.
     */
    private static final Set<String> RESULT_COUNT_KEYS = Set.of("_all", "_filtered", "_missing");

    private static final String OCTET = "(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)";
    private static final Pattern IPV4_LITERAL = Pattern.compile(OCTET + "(\\." + OCTET + "){3}");
    // Starts with a hex digit or ':' and contains ':'. The JDK parses such a string as an IPv6 literal or rejects it,
    // without a lookup. It only resolves names that start with another character. Scoped addresses (with '%') do not
    // match, so they are only compared as plain strings.
    private static final Pattern IPV6_LITERAL = Pattern.compile("(?=.*:)[0-9A-Fa-f:][0-9A-Fa-f:.]*");

    private AggregateBuckets() {
    }

    /**
     * Key of a terms or date histogram bucket. Engines send {@code key_as_string} for formatted values such as
     * dates, and omit it for plain numeric terms, in which case the raw key is used.
     *
     * @param keyAsString the formatted key sent by the engine, or {@code null}
     * @param key         the raw key
     * @return the bucket key
     */
    public static String bucketKey(String keyAsString, Object key) {
        return keyAsString != null ? keyAsString : String.valueOf(key);
    }

    /**
     * Key of a numeric or date range bucket: the engine's key, or else {@code from-to} with {@code *} for an open
     * bound, which is the form the engines generate for unnamed ranges.
     *
     * @param key          the key sent by the engine, or {@code null}
     * @param fromAsString the formatted lower bound, or {@code null}
     * @param from         the raw lower bound, or {@code null} if open
     * @param toAsString   the formatted upper bound, or {@code null}
     * @param to           the raw upper bound, or {@code null} if open
     * @return the bucket key
     */
    public static String rangeBucketKey(String key, String fromAsString, Object from, String toAsString, Object to) {
        if (key != null) {
            return key;
        }
        return rangeKey(fromAsString != null ? fromAsString : Objects.toString(from, null),
                toAsString != null ? toAsString : Objects.toString(to, null));
    }

    /**
     * Key of an IP range bucket. IP range requests cannot carry a key, so the bucket is reported under the key of the
     * {@link IpRange} whose bounds match the bucket's. Any other bucket keeps the engine's key, or {@code from-to} when
     * the engine sends none. A bucket whose bounds match no range at all is logged, since it cannot be labelled.
     *
     * @param aggregate the aggregate the request was built from
     * @param key       the key sent by the engine, or {@code null}
     * @param from      the lower bound sent by the engine, or {@code null} if open
     * @param to        the upper bound sent by the engine, or {@code null} if open
     * @return the bucket key
     */
    public static String ipRangeBucketKey(BaseAggregate aggregate, String key, String from, String to) {
        boolean hasKeyedRange = false;
        boolean matchesUnkeyedRange = false;
        if (aggregate instanceof IpRangeAggregate && ((IpRangeAggregate) aggregate).getRanges() != null) {
            for (IpRange range : ((IpRangeAggregate) aggregate).getRanges()) {
                if (range == null) {
                    continue;
                }
                boolean sameBounds = sameIp(range.getFrom(), from) && sameIp(range.getTo(), to);
                if (range.getKey() != null) {
                    hasKeyedRange = true;
                    if (sameBounds) {
                        return range.getKey();
                    }
                } else if (sameBounds) {
                    matchesUnkeyedRange = true;
                }
            }
        }
        String engineKey = key != null ? key : rangeKey(from, to);
        if (hasKeyedRange && !matchesUnkeyedRange) {
            LOGGER.warn("IP range bucket {} on field {} matches no keyed range, reporting it under the engine key",
                    engineKey, aggregate.getField());
        }
        return engineKey;
    }

    /**
     * Adds a bucket count to aggregate results. Buckets that share a key, such as two ranges with the same key, are
     * summed so that the results stay consistent with the total document count. A bucket named like one of the
     * result counts ({@code _all}, {@code _filtered}, {@code _missing}) is left out and logged, so that it cannot
     * change those counts.
     *
     * @param results  the aggregate results
     * @param key      the bucket key
     * @param docCount the bucket document count
     * @return {@code docCount}, which still counts towards the total document count
     */
    public static long addBucket(Map<String, Long> results, String key, long docCount) {
        if (key != null && RESULT_COUNT_KEYS.contains(key)) {
            LOGGER.warn("Aggregate bucket {} has the name of a result count, its {} documents are left out of the results",
                    key, docCount);
            return docCount;
        }
        results.merge(key, docCount, Long::sum);
        return docCount;
    }

    private static String rangeKey(String from, String to) {
        return (from != null ? from : "*") + "-" + (to != null ? to : "*");
    }

    /**
     * Engines return IPv6 bounds in canonical form (e.g. {@code ::1}), so compare addresses rather than strings.
     * Only strict IP literals are parsed: for those the JDK never falls back to a DNS lookup.
     */
    static boolean sameIp(String a, String b) {
        if (Objects.equals(a, b)) {
            return true;
        }
        if (a == null || b == null || !isIpLiteral(a) || !isIpLiteral(b)) {
            return false;
        }
        try {
            return InetAddress.getByName(a).equals(InetAddress.getByName(b));
        } catch (UnknownHostException e) {
            return false;
        }
    }

    static boolean isIpLiteral(String value) {
        return IPV4_LITERAL.matcher(value).matches() || IPV6_LITERAL.matcher(value).matches();
    }
}
