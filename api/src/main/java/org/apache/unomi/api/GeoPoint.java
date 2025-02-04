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
package org.apache.unomi.api;

import java.util.Map;

/**
 * GeoPoint represents a point in geographical coordinate system using latitude and longitude.
 */
public class GeoPoint {

    /**
     * Latitude of the geo point
     */
    private Double lat;

    /**
     * Longitude of the geo point
     */
    private Double lon;

    /**
     * Instantiates a new GeoPoint
     *
     * @param lat latitude of the geo point
     * @param lon longitude of the geo point
     */
    public GeoPoint(Double lat, Double lon) {
        this.lat = lat;
        this.lon = lon;
    }

    /**
     * Retrieves latitude of the geo point
     *
     * @return geo point latitude
     */
    public Double getLat() {
        return lat;
    }

    /**
     * Retrieves longitude of the geo point
     *
     * @return geo point longitude
     */
    public Double getLon() {
        return lon;
    }

    /**
     * Returns a string representation in the following format: "lat, long"
     *
     * @return String representation of geo point
     */
    public String asString() {
        return lat + ", " + lon;
    }

    /**
     * Calculates distance to geo point using <a href="https://en.wikipedia.org/wiki/Haversine_formula">Haversine formula</a> in meters
     * Note: does not account for altitude
     *
     * @param other GeoPoint to calculate distance to
     * @return Distance in meters
     */
    public double distanceTo(final GeoPoint other) {
        if (other == null) {
            return 0;
        }
        final int R = 6371; // Radius of the earth

        final double lat2 = other.lat;
        final double lon2 = other.lon;

        double latDistance = Math.toRadians(lat2 - lat);
        double lonDistance = Math.toRadians(lon2 - lon);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c * 1000; // convert to meters
    }

    /**
     * Instantiates geo point from map of coordinates
     *
     * @param map Map containing coordinates with keys "lat" and "lon"
     * @return New geo point or null if map is not a valid geo point
     * @throws IllegalArgumentException Thrown if the input is not valid
     */
    public static GeoPoint fromMap(Map<String, Double> map) {
        if (map == null || map.isEmpty() || !map.containsKey("lat") || !map.containsKey("lon")) {
            throw new IllegalArgumentException("Map should be not null and contain \"lat\" and \"lon\" fields");
        }
        return new GeoPoint(map.get("lat"), map.get("lon"));
    }

    /**
     * Instantiates geo point from string representation
     *
     * @param input String geo point representation in the following format: "lat, lon"
     * @return New geo point or null if string is not a valid geo point
     * @throws IllegalArgumentException Thrown if the input is not valid
     */
    public static GeoPoint fromString(final String input) {
        if (input == null || input.trim().length() == 0) {
            return null;
        }
        final String[] parts = input.split(",");
        if (parts.length == 2) {
            try {
                return new GeoPoint(Double.valueOf(parts[0].trim()), Double.valueOf(parts[1].trim()));
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException(String.format("Could not parse \"lat\" or \"lon\" from: %s", input), ex);
            }
        } else {
            throw new IllegalArgumentException(String.format("Incorrect geo point format. Expected: \"lat, lon\", was: %s", input));
        }
    }
}
