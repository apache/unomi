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

package org.jahia.unomi.geonames.services;

import org.oasis_open.contextserver.api.PartialList;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public interface GeonamesService {
    List<String> FEATURES_CLASSES = Arrays.asList("A", "P");
    List<String> COUNTRY_FEATURE_CODES = Arrays.asList("PCL", "PCLD", "PCLF", "PCL", "PCLI", "PCLIX", "PCLS");
    List<String> ADM1_FEATURE_CODES = Arrays.asList("ADM1");
    List<String> ADM2_FEATURE_CODES = Arrays.asList("ADM2");
    List<String> CITIES_FEATURE_CODES = Arrays.asList("PPL", "PPLA", "PPLA2", "PPLA3", "PPLA4", "PPLC", "PPLCH", "PPLF", "PPLG", "PPLL", "PPLR", "PPLR");
    List<List<String>> ORDERED_FEATURES = Arrays.asList(COUNTRY_FEATURE_CODES, ADM1_FEATURE_CODES, ADM2_FEATURE_CODES, CITIES_FEATURE_CODES);

    void importDatabase();

    List<GeonameEntry> reverseGeoCode(String lat, String lon);

    List<GeonameEntry> getHierarchy(String id);

    PartialList<GeonameEntry> getChildrenEntries(List<String> items, int offset, int size);

    PartialList<GeonameEntry> getChildrenCities(List<String> items, int offset, int size);

    List<GeonameEntry> getCapitalEntries(String itemId);
}
