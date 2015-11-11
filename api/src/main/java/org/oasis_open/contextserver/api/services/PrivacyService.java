package org.oasis_open.contextserver.api.services;

import org.oasis_open.contextserver.api.ServerInfo;

import java.util.List;

/**
 * Created by loom on 10.09.15.
 */
public interface PrivacyService {

    public static final String GLOBAL_ANONYMOUS_PROFILE_ID = "global-anonymous-profile";

    ServerInfo getServerInfo();

    Boolean deleteProfile(String profileId);

    String anonymizeBrowsingData(String profileId);

    Boolean deleteProfileData(String profileId);

    Boolean setAnonymous(String profileId, boolean anonymous);

    Boolean isAnonymous(String profileId);

    List<String> getFilteredEventTypes(String profileId);

    Boolean setFilteredEventTypes(String profileId, List<String> eventTypes);

    List<String> getDeniedProperties(String profileId);

    Boolean setDeniedProperties(String profileId, List<String> propertyNames);

    List<String> getDeniedPropertyDistribution(String profileId);

    Boolean setDeniedPropertyDistribution(String profileId, List<String> propertyNames);

    Boolean removeProperty(String profileId, String propertyName);

}
