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

package org.apache.unomi.graphql.types.output.property;

import org.apache.unomi.api.PropertyType;
import org.apache.unomi.api.query.NumericRange;
import org.apache.unomi.graphql.types.output.CDPPropertyInterface;

import static org.apache.unomi.graphql.CDPGraphQLConstants.DEFAULT_RANGE_NAME;

public class CDPPropertyType implements CDPPropertyInterface {

    protected PropertyType type;

    public CDPPropertyType(PropertyType type) {
        this.type = type;
    }

    @Override
    public PropertyType getType() {
        return type;
    }

    protected NumericRange getDefaultNumericRange() {
        if (type == null || type.getNumericRanges() == null) {
            return null;
        }
        return type.getNumericRanges().stream()
                .filter(range -> DEFAULT_RANGE_NAME.equals(range.getKey()))
                .findFirst()
                .orElse(null);
    }
}
