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
package org.apache.unomi.graphql.types.input;

import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import org.apache.unomi.graphql.types.output.CDPGeoDistanceUnit;
import org.apache.unomi.graphql.types.output.CDPGeoPoint;

@GraphQLName("CDP_GeoDistanceInput")
public class CDPGeoDistanceInput {

    @GraphQLField
    private CDPGeoPoint center;

    @GraphQLField
    private CDPGeoDistanceUnit unit;

    @GraphQLField
    private Double distance;

    public CDPGeoDistanceInput(
            final @GraphQLName("center") CDPGeoPoint center,
            final @GraphQLName("unit") CDPGeoDistanceUnit unit,
            final @GraphQLName("distance") Double distance) {
        this.center = center;
        this.unit = unit;
        this.distance = distance;
    }

    public CDPGeoPoint getCenter() {
        return center;
    }

    public CDPGeoDistanceUnit getUnit() {
        return unit;
    }

    public Double getDistance() {
        return distance;
    }
}
