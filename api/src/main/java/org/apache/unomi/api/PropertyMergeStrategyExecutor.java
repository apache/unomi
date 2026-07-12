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

import java.util.List;

/**
 * Algorithm that merges two values for the same profile property.
 * Implementations define strategies such as keep latest, sum numbers, or
 * union lists when events update the same field.
 */
public interface PropertyMergeStrategyExecutor {
    /**
     * Merges the value of the property identified by the specified name and type from the specified profiles into the specified target profile.
     *
     * @param propertyName    the name of the property to be merged
     * @param propertyType    the type of the property to be merged
     * @param profilesToMerge a List of profiles to merge
     * @param targetProfile   the target profile into which the specified profiles will be merged
     * @return {@code true} if the target profile was successfully modified as the result of the merge, {@code false} otherwise
     */
    boolean mergeProperty(String propertyName, PropertyType propertyType, List<Profile> profilesToMerge, Profile targetProfile);

}
