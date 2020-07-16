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
package org.apache.unomi.itests.graphql;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.unomi.api.Consent;
import org.apache.unomi.api.ConsentStatus;
import org.apache.unomi.api.Profile;
import org.apache.unomi.api.services.ProfileService;
import org.apache.unomi.graphql.utils.DateUtils;
import org.junit.Assert;
import org.junit.Test;
import org.ops4j.pax.exam.util.Filter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class GraphQLProfilePropertiesIT extends BaseGraphQLIT {

    @Inject
    @Filter(timeout = 600000)
    protected ProfileService profileService;

    private final static Logger LOGGER = LoggerFactory.getLogger(GraphQLProfilePropertiesIT.class);

    @Test
    public void testCreateAndDeleteProfileProperty() throws Exception {
        try (CloseableHttpResponse response = post("graphql/profile/create-or-update-profile-properties.json")) {
            final ResponseContext context = ResponseContext.parse(response.getEntity());

            Assert.assertTrue(context.getValue("data.cdp.createOrUpdateProfileProperties"));
        }

        keepTrying("Failed waiting for the creation of the property for profiles", () -> profileService.getPropertyType("testProperty"), Objects::nonNull, 1000, 100);

        final Profile profile = new Profile("profileId_createOrUpdateProfilePropertiesTest");
        Map<String, String> testPropertyMap = new HashMap<>();
        testPropertyMap.put("testStringProperty", "testStringPropertyValue");
        testPropertyMap.put("testLongProperty", String.valueOf(9007199254740991L));
        profile.setProperty("testProperty", testPropertyMap);
        profileService.save(profile);

        Profile newProfile = keepTrying("Failed waiting for the creation of the profile", () -> profileService.load(profile.getItemId()), Objects::nonNull, 1000, 100);
        HashMap testProperty = (HashMap) newProfile.getProperty("testProperty");
        Assert.assertNotNull(testProperty);
        Assert.assertEquals("testStringPropertyValue", testProperty.get("testStringProperty"));
        Assert.assertEquals(String.valueOf(9007199254740991L), testProperty.get("testLongProperty"));

        try (CloseableHttpResponse response = post("graphql/profile/get-profile-with-new-property.json")) {
            final ResponseContext context = ResponseContext.parse(response.getEntity());

            Assert.assertNotNull(context.getValue("data.cdp.getProfile.testProperty"));
            Assert.assertEquals("testStringPropertyValue", context.getValue("data.cdp.getProfile.testProperty.testStringProperty"));
            Assert.assertEquals(9007199254740991L, (long) context.getValue("data.cdp.getProfile.testProperty.testLongProperty"));
        }

        try (CloseableHttpResponse response = post("graphql/profile/delete-profile-properties.json")) {
            final ResponseContext context = ResponseContext.parse(response.getEntity());

            Assert.assertTrue(context.getValue("data.cdp.deleteProfileProperties"));
        }
    }

    @Test
    public void testGetProfile_CDPFields() throws Exception {
        final Consent consent = new Consent();

        consent.setTypeIdentifier("newsletter1");
        consent.setScope("digitall");
        consent.setStatus(ConsentStatus.GRANTED);
        Date statusDate = DateUtils.toDate(OffsetDateTime.parse("2019-05-15T14:47:28Z"));
        consent.setStatusDate(statusDate);
        Date revokationDate = DateUtils.toDate(OffsetDateTime.parse("2021-05-14T14:47:28Z"));
        consent.setRevokeDate(revokationDate);

        final Map<String, Double> interestsAsMap = new HashMap<>();

        interestsAsMap.put("interestName", 0.5);

        final Profile profile = new Profile("profileId_testGetProfile_CDPFields");

        profile.setConsent(consent);
        profile.setProperty("interests", interestsAsMap);

        profileService.save(profile);

        keepTrying("Failed waiting for the creation of the profile for the \"testGetProfile_CDPFields\" test",
                () -> profileService.load(profile.getItemId()), Objects::nonNull, 1000, 100);

        try (CloseableHttpResponse response = post("graphql/profile/get-profile-fields.json")) {
            final ResponseContext context = ResponseContext.parse(response.getEntity());

            System.out.println(context.getResponseAsMap());

            // assert consent
            Assert.assertEquals("GRANTED", context.getValue("data.cdp.getProfile.cdp_consents[0].status"));
            Assert.assertEquals("newsletter1", context.getValue("data.cdp.getProfile.cdp_consents[0].type"));
            Assert.assertEquals(statusDate, DateUtils.toDate(OffsetDateTime.parse(context.getValue("data.cdp.getProfile.cdp_consents[0].lastUpdate"))));
            Assert.assertEquals(revokationDate, DateUtils.toDate(OffsetDateTime.parse(context.getValue("data.cdp.getProfile.cdp_consents[0].expiration"))));

            // assert interests
            Assert.assertEquals("interestName", context.getValue("data.cdp.getProfile.cdp_interests[0].topic"));
            Assert.assertEquals(0.5, context.getValue("data.cdp.getProfile.cdp_interests[0].score"), 0.0);
        }
    }

}
