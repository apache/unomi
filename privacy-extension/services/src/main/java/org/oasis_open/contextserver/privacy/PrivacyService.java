package org.oasis_open.contextserver.privacy;

import java.util.List;

/**
 * Created by loom on 10.09.15.
 */
public interface PrivacyService {

    ServerInfo getServerInfo();

    Boolean deleteProfileData(String profileId);

    Boolean surfAnonymously(String profileId);

    List<String> getFilteredEventTypes(String profileId);

    Boolean setFilteredEventTypes(String profileId, List<String> eventTypes);

    List<String> getDeniedProperties(String profileId);

    Boolean setDeniedProperties(String profileId, List<String> propertyNames);

    List<String> getDeniedPropertyDistribution(String profileId);

    Boolean setDeniedPropertyDistribution(String profileId, List<String> propertyNames);

}
