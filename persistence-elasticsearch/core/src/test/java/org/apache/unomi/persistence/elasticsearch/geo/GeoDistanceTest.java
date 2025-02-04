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
package org.apache.unomi.persistence.elasticsearch.geo;

import org.elasticsearch.common.geo.GeoDistance;
import org.elasticsearch.common.unit.DistanceUnit;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class GeoDistanceTest {

    private static final double SRC_LAT = 40.7128; // Example source latitude
    private static final double SRC_LON = -74.0060; // Example source longitude
    private static final double DST_LAT = 34.0522; // Example destination latitude
    private static final double DST_LON = -118.2437; // Example destination longitude

    @Test
    public void testFromString() {
        assertEquals(GeoDistance.PLANE, GeoDistance.fromString("plane"));
        assertEquals(GeoDistance.ARC, GeoDistance.fromString("arc"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFromStringInvalid() {
        GeoDistance.fromString("invalid");
    }

    @Test
    public void testCalculatePlane() {
        // Assuming GeoUtils.planeDistance returns 3940.0 km for the given coordinates
        double expectedDistanceInMeters = 3978199.0100920075;
        double actualDistance = GeoDistance.PLANE.calculate(SRC_LAT, SRC_LON, DST_LAT, DST_LON, DistanceUnit.METERS);
        assertEquals(expectedDistanceInMeters, actualDistance, 0.01);
    }

    @Test
    public void testCalculateArc() {
        // Assuming GeoUtils.arcDistance returns 3971.0 km for the given coordinates
        double expectedDistanceInMeters = 3935751.673226063;
        double actualDistance = GeoDistance.ARC.calculate(SRC_LAT, SRC_LON, DST_LAT, DST_LON, DistanceUnit.METERS);
        assertEquals(expectedDistanceInMeters, actualDistance, 0.01);
    }

}
