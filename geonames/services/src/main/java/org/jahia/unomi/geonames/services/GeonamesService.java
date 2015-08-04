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
}
