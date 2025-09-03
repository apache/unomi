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

public enum GeoDistance {
    HAVERSINE {
        @Override
        public double calculate(double lat1, double lon1, double lat2, double lon2, DistanceUnit unit) {
            double latRad1 = toRadians(lat1);
            double latRad2 = toRadians(lat2);
            double deltaLat = toRadians(lat2 - lat1);
            double deltaLon = toRadians(lon2 - lon1);

            double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                    + Math.cos(latRad1) * Math.cos(latRad2)
                    * Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);

            double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
            return unit.convert(EARTH_MEAN_RADIUS * c, DistanceUnit.METERS);
        }
    },
    ARC {
        @Override
        public double calculate(double lat1, double lon1, double lat2, double lon2, DistanceUnit unit) {
            double latRad1 = toRadians(lat1);
            double lonRad1 = toRadians(lon1);
            double latRad2 = toRadians(lat2);
            double lonRad2 = toRadians(lon2);

            double deltaLon = lonRad2 - lonRad1;

            double cosTheta = Math.sin(latRad1) * Math.sin(latRad2)
                    + Math.cos(latRad1) * Math.cos(latRad2) * Math.cos(deltaLon);

            double theta = Math.acos(Math.min(1.0, Math.max(-1.0, cosTheta))); // Clamp to avoid NaN
            return unit.convert(EARTH_MEAN_RADIUS * theta, DistanceUnit.METERS);
        }
    },
    PLANE {
        @Override
        public double calculate(double lat1, double lon1, double lat2, double lon2, DistanceUnit unit) {
            double x = toRadians(lon2 - lon1) * Math.cos(toRadians((lat1 + lat2) / 2));
            double y = toRadians(lat2 - lat1);
            double distance = Math.sqrt(x * x + y * y) * EARTH_MEAN_RADIUS;
            return unit.convert(distance, DistanceUnit.METERS);
        }
    };

    public static final double EARTH_MEAN_RADIUS = 6371008.7714D;      // meters (WGS 84)

    public static final double TO_RADIANS = Math.PI / 180D;
    public static final double TO_DEGREES = 180D / Math.PI;

    public static double toRadians(double degrees) {
        return degrees * TO_RADIANS;
    }

    /**
     * Calculate the distance between two geographic points and return it in the specified unit.
     *
     * @param lat1 Latitude of the first point.
     * @param lon1 Longitude of the first point.
     * @param lat2 Latitude of the second point.
     * @param lon2 Longitude of the second point.
     * @param unit The distance unit for the result.
     * @return The calculated distance in the specified unit.
     */
    public abstract double calculate(double lat1, double lon1, double lat2, double lon2, DistanceUnit unit);

    /**
     * Converts a string representation of a GeoDistance method to its corresponding enum.
     *
     * @param name The string representation of the GeoDistance method (e.g., "plane" or "arc").
     * @return The corresponding GeoDistance enum.
     * @throws IllegalArgumentException If the name does not match any known GeoDistance methods.
     */
    public static GeoDistance fromString(String name) {
        if ("plane".equalsIgnoreCase(name)) {
            return PLANE;
        } else if ("arc".equalsIgnoreCase(name)) {
            return ARC;
        } else if ("haversine".equalsIgnoreCase(name)) {
            return HAVERSINE;
        } else {
            throw new IllegalArgumentException("Unknown GeoDistance method: " + name);
        }
    }
}
