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

import java.util.HashMap;
import java.util.Map;

/**
 * Units of distance and conversion helpers compatible with those used historically by Elasticsearch.
 * <p>
 * This enum replaces prior Elasticsearch utilities with a 100% compatible implementation hosted
 * within Unomi, allowing us to remove the dependency while retaining identical behavior in the
 * persistence layer and tests.
 *
 * TODO maybe evaluate https://github.com/unitsofmeasurement/indriya instead of this implementation
 * to see if it can be a 100% compatible replacement.
 */
public enum DistanceUnit {
    KILOMETERS(1000.0, "km", "kilometers"),
    MILES(1609.344, "mi", "miles"),
    YARDS(0.9144, "yd", "yards"),
    FEET(0.3048, "ft", "feet"),
    INCHES(0.0254, "in", "inches"),
    NAUTICAL_MILES(1852.0, "NM", "nauticalmiles"),
    METERS(1.0, "m", "meters");

    // Constants for Earth's properties
    private static final double EARTH_SEMI_MAJOR_AXIS = 6378137.0; // in meters
    private static final double EARTH_EQUATOR = 2*Math.PI * EARTH_SEMI_MAJOR_AXIS; // in meters

    private static final Map<String, DistanceUnit> UNIT_MAP = new HashMap<>();

    public static final DistanceUnit DEFAULT = METERS;

    static {
        for (DistanceUnit unit : values()) {
            for (String alias : unit.aliases) {
                UNIT_MAP.put(alias.toLowerCase(), unit);
            }
        }
    }

    private final double metersPerUnit;
    private final String[] aliases;

    DistanceUnit(double metersPerUnit, String... aliases) {
        this.metersPerUnit = metersPerUnit;
        this.aliases = aliases;
    }

    /**
     * Returns the Earth's circumference in this unit.
     *
     * @return the circumference
     */
    public double getEarthCircumference() {
        return EARTH_EQUATOR / metersPerUnit;
    }

    /**
     * Returns the Earth's radius in this unit.
     *
     * @return the radius
     */
    public double getEarthRadius() {
        return EARTH_SEMI_MAJOR_AXIS / metersPerUnit;
    }

    /**
     * Returns the distance per degree of latitude in this unit.
     *
     * @return the distance per degree
     */
    public double getDistancePerDegree() {
        return EARTH_EQUATOR / (360.0 * metersPerUnit);
    }

    /**
     * Converts a value in this unit to meters.
     *
     * @param value the value in this unit
     * @return the value in meters
     */
    public double toMeters(double value) {
        return value * metersPerUnit;
    }

    /**
     * Converts a value from meters to this unit.
     *
     * @param value the value in meters
     * @return the value in this unit
     */
    public double fromMeters(double value) {
        return value / metersPerUnit;
    }

    /**
     * Converts a value from this unit to another unit.
     *
     * @param value the value in this unit
     * @param toUnit the target unit
     * @return the converted value
     */
    public double convert(double value, DistanceUnit toUnit) {
        return (value * metersPerUnit) / toUnit.metersPerUnit;
    }

    /**
     * Converts a value between two distance units.
     *
     * @param value the value to convert
     * @param from the source unit
     * @param to the target unit
     * @return the converted value
     */
    public static double convert(double value, DistanceUnit from, DistanceUnit to) {
        return (value * from.metersPerUnit) / to.metersPerUnit;
    }

    /**
     * Parses a distance unit from its string representation.
     *
     * @param unit the unit name or alias
     * @return the matching distance unit
     */
    public static DistanceUnit fromString(String unit) {
        if (unit == null || unit.isEmpty()) {
            throw new IllegalArgumentException("Unit string must not be null or empty");
        }
        DistanceUnit distanceUnit = UNIT_MAP.get(unit.toLowerCase());
        if (distanceUnit == null) {
            throw new IllegalArgumentException("Unknown distance unit: " + unit);
        }
        return distanceUnit;
    }

    /**
     * Parses the unit suffix from a distance string.
     *
     * @param distance the distance string (e.g. {@code 10km})
     * @param defaultUnit the unit to use when no suffix is found
     * @return the parsed distance unit
     */
    public static DistanceUnit parseUnit(String distance, DistanceUnit defaultUnit) {
        for (DistanceUnit unit : values()) {
            for (String alias : unit.aliases) {
                if (distance.endsWith(alias)) {
                    return unit;
                }
            }
        }
        return defaultUnit;
    }

    /**
     * Parses a distance string and returns the value in this unit.
     *
     * @param distance the distance string (e.g. {@code 10km})
     * @param defaultUnit the unit to use when no suffix is found
     * @return the distance in this unit
     */
    public double parse(String distance, DistanceUnit defaultUnit) {
        Distance parsed = Distance.parseDistance(distance, defaultUnit);
        return convert(parsed.value, parsed.unit, this);
    }

    @Override
    public String toString() {
        return aliases[0];
    }

    /**
     * A distance value paired with its unit.
     */
    public static class Distance {
        public final double value;
        public final DistanceUnit unit;

        /**
         * Creates a distance value.
         *
         * @param value the numeric distance
         * @param unit the distance unit
         */
        public Distance(double value, DistanceUnit unit) {
            this.value = value;
            this.unit = unit;
        }

        /**
         * Converts this distance to another unit.
         *
         * @param toUnit the target unit
         * @return a new distance in the target unit
         */
        public Distance convert(DistanceUnit toUnit) {
            double convertedValue = DistanceUnit.convert(value, unit, toUnit);
            return new Distance(convertedValue, toUnit);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            Distance other = (Distance) obj;
            return Double.compare(DistanceUnit.convert(value, unit, other.unit), other.value) == 0;
        }

        @Override
        public int hashCode() {
            return Double.hashCode(value * unit.metersPerUnit);
        }

        @Override
        public String toString() {
            return value + " " + unit.toString();
        }

        /**
         * Parses a distance string using meters as the default unit.
         *
         * @param distance the distance string
         * @return the parsed distance
         */
        public static Distance parseDistance(String distance) {
            return parseDistance(distance, DistanceUnit.METERS);
        }

        /**
         * Parses a distance string with a default unit.
         *
         * @param distance the distance string
         * @param defaultUnit the unit when no suffix is found
         * @return the parsed distance
         */
        public static Distance parseDistance(String distance, DistanceUnit defaultUnit) {
            for (DistanceUnit unit : values()) {
                for (String alias : unit.aliases) {
                    if (distance.endsWith(alias)) {
                        String valuePart = distance.substring(0, distance.length() - alias.length()).trim();
                        return new Distance(Double.parseDouble(valuePart), unit);
                    }
                }
            }
            return new Distance(Double.parseDouble(distance), defaultUnit);
        }
    }
}
