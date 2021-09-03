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

package org.apache.unomi.graphql.converters;

import graphql.Scalars;
import graphql.scalars.ExtendedScalars;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLType;
import org.apache.unomi.graphql.scalars.DateTimeFunction;
import org.apache.unomi.graphql.scalars.GeoPointFunction;
import org.apache.unomi.graphql.scalars.JSONFunction;
import org.apache.unomi.graphql.schema.PropertyNameTranslator;
import org.apache.unomi.graphql.utils.StringUtils;

public interface UnomiToGraphQLConverter {

    /*
     *  Convert all unomi value types to graphql types
     *  Also able to handle array and required notation in the following format
     *  [<type>]! - required array of values <type>
     */
    static GraphQLType convertPropertyType(final String type) {
        String normalizedType = type;
        GraphQLType graphQLType;
        boolean isArray = false;
        boolean isMandatory = false;
        if (normalizedType.endsWith("!")) {
            isMandatory = true;
            normalizedType = normalizedType.substring(0, normalizedType.length() - 1);
        }
        if (normalizedType.startsWith("[") && normalizedType.endsWith("]")) {
            isArray = true;
            normalizedType = normalizedType.substring(1, normalizedType.length() - 1);
        }
        switch (normalizedType) {
            case "id":
                isMandatory = true; // force mandatory for id
                graphQLType = Scalars.GraphQLID;
                break;
            case "integer":
                graphQLType = Scalars.GraphQLInt;
                break;
            case "long":
                graphQLType = ExtendedScalars.GraphQLLong;
                break;
            case "float":
                graphQLType = Scalars.GraphQLFloat;
                break;
            case "set":
            case "json":
                graphQLType = JSONFunction.JSON_SCALAR;
                break;
            case "geopoint":
                graphQLType = GeoPointFunction.GEOPOINT_SCALAR;
                break;
            case "date":
                graphQLType = DateTimeFunction.DATE_TIME_SCALAR;
                break;
            case "boolean":
                graphQLType = Scalars.GraphQLBoolean;
                break;
            case "string":
            default:
                graphQLType = Scalars.GraphQLString;
                break;
        }
        graphQLType = isArray ? GraphQLList.list(graphQLType) : graphQLType;
        return isMandatory ? GraphQLNonNull.nonNull(graphQLType) : graphQLType;
    }

    static String convertEventType(final String eventType) {
        return StringUtils.capitalize(PropertyNameTranslator.translateFromUnomiToGraphQL(eventType)) + "Event";
    }
}
