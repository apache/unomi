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
package org.apache.unomi.persistence.spi.conditions.geo;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DistanceUnitTest {

    // Local constants
    /** Earth ellipsoid major axis defined by WGS 84 in meters */
    public static final double EARTH_SEMI_MAJOR_AXIS = 6378137.0;      // meters (WGS 84)

    /** Earth ellipsoid minor axis defined by WGS 84 in meters */
    public static final double EARTH_SEMI_MINOR_AXIS = 6356752.314245; // meters (WGS 84)

    /** Earth mean radius defined by WGS 84 in meters */
    public static final double EARTH_MEAN_RADIUS = 6371008.7714D;      // meters (WGS 84)

    /** Earth axis ratio defined by WGS 84 (0.996647189335) */
    public static final double EARTH_AXIS_RATIO = EARTH_SEMI_MINOR_AXIS / EARTH_SEMI_MAJOR_AXIS;

    /** Earth ellipsoid equator length in meters */
    public static final double EARTH_EQUATOR = 2*Math.PI * EARTH_SEMI_MAJOR_AXIS;

    /** Earth ellipsoid polar distance in meters */
    public static final double EARTH_POLAR_DISTANCE = Math.PI * EARTH_SEMI_MINOR_AXIS;

    @Test
    public void testEarthCircumference() {
        assertEquals(EARTH_EQUATOR / DistanceUnit.KILOMETERS.toMeters(1.0), DistanceUnit.KILOMETERS.getEarthCircumference(), 0.0001);
        assertEquals(EARTH_EQUATOR, DistanceUnit.METERS.getEarthCircumference(), 0.0001);
    }

    @Test
    public void testEarthRadius() {
        assertEquals(EARTH_SEMI_MAJOR_AXIS / DistanceUnit.MILES.toMeters(1.0), DistanceUnit.MILES.getEarthRadius(), 0.0001);
        assertEquals(EARTH_SEMI_MAJOR_AXIS, DistanceUnit.METERS.getEarthRadius(), 0.0001);
    }

    @Test
    public void testDistancePerDegree() {
        assertEquals(EARTH_EQUATOR / 360.0, DistanceUnit.METERS.getDistancePerDegree(), 0.0001);
        assertEquals(EARTH_EQUATOR / (360.0 * DistanceUnit.KILOMETERS.toMeters(1.0)), DistanceUnit.KILOMETERS.getDistancePerDegree(), 0.0001);
    }

    @Test
    public void testToMeters() {
        assertEquals(1000.0, DistanceUnit.KILOMETERS.toMeters(1.0), 0.0001);
        assertEquals(1609.344, DistanceUnit.MILES.toMeters(1.0), 0.0001);
        assertEquals(1.0, DistanceUnit.METERS.toMeters(1.0), 0.0001);
    }

    @Test
    public void testFromMeters() {
        assertEquals(1.0, DistanceUnit.KILOMETERS.fromMeters(1000.0), 0.0001);
        assertEquals(1.0, DistanceUnit.MILES.fromMeters(1609.344), 0.0001);
        assertEquals(1.0, DistanceUnit.METERS.fromMeters(1.0), 0.0001);
    }

    @Test
    public void testConvert() {
        assertEquals(1.0, DistanceUnit.convert(1.0, DistanceUnit.KILOMETERS, DistanceUnit.KILOMETERS), 0.0001);
        assertEquals(1000.0, DistanceUnit.convert(1.0, DistanceUnit.KILOMETERS, DistanceUnit.METERS), 0.0001);
        assertEquals(1.0, DistanceUnit.convert(1609.344, DistanceUnit.METERS, DistanceUnit.MILES), 0.0001);
    }

    @Test
    public void testToString() {
        assertEquals("km", DistanceUnit.KILOMETERS.toString());
        assertEquals("mi", DistanceUnit.MILES.toString());
    }

    @Test
    public void testParse() {
        assertEquals(1.0, DistanceUnit.MILES.parse("1mi", DistanceUnit.METERS), 0.0001);
        assertEquals(1609.344, DistanceUnit.METERS.parse("1mi", DistanceUnit.METERS), 0.0001);
        assertEquals(1000.0, DistanceUnit.METERS.parse("1km", DistanceUnit.METERS), 0.0001);
        assertEquals(1.0, DistanceUnit.KILOMETERS.parse("1km", DistanceUnit.KILOMETERS), 0.0001);
    }

    @Test
    public void testParseUnit() {
        assertEquals(DistanceUnit.KILOMETERS, DistanceUnit.parseUnit("1km", DistanceUnit.METERS));
        assertEquals(DistanceUnit.MILES, DistanceUnit.parseUnit("1mi", DistanceUnit.METERS));
        assertEquals(DistanceUnit.METERS, DistanceUnit.parseUnit("1", DistanceUnit.METERS));
    }

    @Test
    public void testDistanceParsing() {
        DistanceUnit.Distance distance = DistanceUnit.Distance.parseDistance("1km");
        assertEquals(1.0, distance.value, 0.0001);
        assertEquals(DistanceUnit.KILOMETERS, distance.unit);

        DistanceUnit.Distance defaultDistance = DistanceUnit.Distance.parseDistance("100");
        assertEquals(100.0, defaultDistance.value, 0.0001);
        assertEquals(DistanceUnit.DEFAULT, defaultDistance.unit);
    }

    @Test
    public void testDistanceConversion() {
        DistanceUnit.Distance distance = new DistanceUnit.Distance(1.0, DistanceUnit.KILOMETERS);
        DistanceUnit.Distance converted = distance.convert(DistanceUnit.METERS);
        assertEquals(1000.0, converted.value, 0.0001);
        assertEquals(DistanceUnit.METERS, converted.unit);
    }

    @Test
    public void testDistanceEqualsAndHashCode() {
        DistanceUnit.Distance d1 = new DistanceUnit.Distance(1.0, DistanceUnit.KILOMETERS);
        DistanceUnit.Distance d2 = new DistanceUnit.Distance(1000.0, DistanceUnit.METERS);
        assertEquals(d1, d2);
        assertEquals(d1.hashCode(), d2.hashCode());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFromStringInvalidUnit() {
        DistanceUnit.fromString("invalid");
    }

}
