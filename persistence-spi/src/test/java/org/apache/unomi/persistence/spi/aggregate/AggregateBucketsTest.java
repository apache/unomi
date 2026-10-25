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
import org.junit.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AggregateBucketsTest {

    @Test
    public void bucketKeyPrefersTheFormattedKey() {
        assertEquals("2020-01-01", AggregateBuckets.bucketKey("2020-01-01", 1577836800000L));
        assertEquals("70", AggregateBuckets.bucketKey(null, 70L));
        assertEquals("2.5", AggregateBuckets.bucketKey(null, 2.5d));
    }

    @Test
    public void rangeBucketKeyFallsBackToTheEngineFormat() {
        assertEquals("adult", AggregateBuckets.rangeBucketKey("adult", null, 18.0, null, 65.0));
        assertEquals("18.0-65.0", AggregateBuckets.rangeBucketKey(null, null, 18.0, null, 65.0));
        assertEquals("*-2020-01-02", AggregateBuckets.rangeBucketKey(null, null, null, "2020-01-02", 1577923200000.0));
        assertEquals("*-*", AggregateBuckets.rangeBucketKey(null, null, null, null, null));
    }

    @Test
    public void ipRangeBucketKeyUsesTheMatchingRangeKey() {
        IpRangeAggregate aggregate = new IpRangeAggregate("properties.ip",
                Arrays.asList(ipRange("lan", "10.0.0.0", "10.0.1.0"), ipRange("loopback", "::1", null), null));

        assertEquals("lan", AggregateBuckets.ipRangeBucketKey(aggregate, "10.0.0.0-10.0.1.0", "10.0.0.0", "10.0.1.0"));
        // engines return IPv6 bounds in canonical form
        assertEquals("loopback", AggregateBuckets.ipRangeBucketKey(
                new IpRangeAggregate("properties.ip", Arrays.asList(ipRange("loopback", "0:0:0:0:0:0:0:1", null))),
                "::1-*", "::1", null));
    }

    @Test
    public void ipRangeBucketKeyKeepsTheEngineKeyWithoutAMatch() {
        IpRangeAggregate aggregate = new IpRangeAggregate("properties.ip",
                Arrays.asList(ipRange("lan", "10.0.0.0", "10.0.1.0")));

        assertEquals("192.168.0.0-*", AggregateBuckets.ipRangeBucketKey(aggregate, "192.168.0.0-*", "192.168.0.0", null));
        assertEquals("192.168.0.0-*", AggregateBuckets.ipRangeBucketKey(aggregate, null, "192.168.0.0", null));
        assertEquals("10.0.0.0-10.0.1.0", AggregateBuckets.ipRangeBucketKey(
                new TermsAggregate("properties.ip"), "10.0.0.0-10.0.1.0", "10.0.0.0", "10.0.1.0"));
    }

    @Test
    public void addBucketSumsBucketsThatShareAKey() {
        Map<String, Long> results = new LinkedHashMap<>();
        assertEquals(2L, AggregateBuckets.addBucket(results, "adult", 2L));
        assertEquals(3L, AggregateBuckets.addBucket(results, "adult", 3L));
        assertEquals(Long.valueOf(5L), results.get("adult"));
    }

    @Test
    public void sameIpComparesAddresses() {
        assertTrue(AggregateBuckets.sameIp("::1", "0:0:0:0:0:0:0:1"));
        assertTrue(AggregateBuckets.sameIp("1.2.3.4", "::ffff:1.2.3.4"));
        assertTrue(AggregateBuckets.sameIp(null, null));
        assertFalse(AggregateBuckets.sameIp("10.0.0.1", "10.0.0.2"));
        assertFalse(AggregateBuckets.sameIp("10.0.0.1", null));
    }

    @Test
    public void onlyStrictIpLiteralsAreParsed() {
        assertTrue(AggregateBuckets.isIpLiteral("10.0.0.1"));
        assertTrue(AggregateBuckets.isIpLiteral("::1"));
        assertTrue(AggregateBuckets.isIpLiteral("fe80::1"));
        // these would make InetAddress.getByName resolve a host name
        assertFalse(AggregateBuckets.isIpLiteral("999.1.1.1"));
        assertFalse(AggregateBuckets.isIpLiteral("localhost"));
        assertFalse(AggregateBuckets.isIpLiteral("zz:1"));
        assertFalse(AggregateBuckets.isIpLiteral(".:1"));
        assertFalse(AggregateBuckets.sameIp("localhost", "127.0.0.1"));
    }

    private static IpRange ipRange(String key, String from, String to) {
        IpRange range = new IpRange();
        range.setKey(key);
        range.setFrom(from);
        range.setTo(to);
        return range;
    }
}
