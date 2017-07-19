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

package org.apache.unomi.sfdc.services.internal;

import org.apache.http.HttpException;
import org.apache.unomi.api.Profile;
import org.apache.unomi.sfdc.services.SFDCConfiguration;
import org.cometd.bayeux.Message;
import org.cometd.bayeux.client.ClientSessionChannel;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * A unit test class for testing the Salesforce Service implementation
 */
public class SFDCServiceImplTest {

    private static SFDCServiceImpl sfdcServiceImpl;
    private static boolean canRunTests = false;
    private static SFDCConfiguration sfdcConfiguration;

    @BeforeClass
    public static void setUp() throws IOException {
        sfdcServiceImpl = new SFDCServiceImpl();
        // we must now configure it.
        InputStream configPropertiesStream = SFDCServiceImplTest.class.getClassLoader().getResourceAsStream("org.apache.unomi.sfdc.cfg");
        Properties properties = new Properties();
        properties.load(configPropertiesStream);
        sfdcConfiguration = new SFDCConfiguration();
        sfdcConfiguration.setSfdcLoginEndpoint(properties.getProperty("sfdc.login.endpoint"));
        sfdcConfiguration.setSfdcUserUsername(properties.getProperty("sfdc.user.username"));
        sfdcConfiguration.setSfdcUserPassword(properties.getProperty("sfdc.user.password"));
        sfdcConfiguration.setSfdcUserSecurityToken(properties.getProperty("sfdc.user.securityToken"));
        sfdcConfiguration.setSfdcConsumerKey(properties.getProperty("sfdc.consumer.key"));
        sfdcConfiguration.setSfdcConsumerSecret(properties.getProperty("sfdc.consumer.secret"));
        sfdcConfiguration.setSfdcChannel(properties.getProperty("sfdc.channel"));
        sfdcConfiguration.setSfdcFieldMappings(properties.getProperty("sfdc.fields.mappings"));
        sfdcConfiguration.setSfdcFieldMappingsIdentifier(properties.getProperty("sfdc.fields.mappings.identifier"));
        if (System.getProperty("sfdcProperties") != null) {
            Properties testProperties = new Properties();
            InputStream testPropertiesInputStream = new FileInputStream(System.getProperty("sfdcProperties"));
            testProperties.load(testPropertiesInputStream);
            sfdcConfiguration.setSfdcLoginEndpoint(testProperties.getProperty("sfdc.login.endpoint"));
            sfdcConfiguration.setSfdcUserUsername(testProperties.getProperty("sfdc.user.username"));
            sfdcConfiguration.setSfdcUserPassword(testProperties.getProperty("sfdc.user.password"));
            sfdcConfiguration.setSfdcUserSecurityToken(testProperties.getProperty("sfdc.user.securityToken"));
            sfdcConfiguration.setSfdcConsumerKey(testProperties.getProperty("sfdc.consumer.key"));
            sfdcConfiguration.setSfdcConsumerSecret(testProperties.getProperty("sfdc.consumer.secret"));
            canRunTests = true;
            sfdcServiceImpl.setDefaultSFDCConfiguration(sfdcConfiguration);
            sfdcServiceImpl.start();
        } else {
            System.out.println("CANNOT RUN TESTS, PLEASE PROVIDE A PROPERTIES FILE WITH SALESFORCE CREDENTIALS AND REFERENCING IT USING -DsfdcProperties=FILEPATH !!!!!!");
        }
    }

    @AfterClass
    public static void shutdown() {
        if (canRunTests) {
            sfdcServiceImpl.stop();
            sfdcServiceImpl = null;
        }
    }

    private boolean checkCanRunTests() {
        if (!canRunTests) {
            System.out.println("CANNOT RUN TESTS, PLEASE PROVIDE A PROPERTIES FILE WITH SALESFORCE CREDENTIALS AND REFERENCING IT USING -DsfdcProperties=FILEPATH !!!!!!");
        }
        return canRunTests;
    }

    @Test
    public void testGetLeads() {
        if (!checkCanRunTests()) return;
        Set<String> recentLeadIds = sfdcServiceImpl.getRecentLeadIds();
        if (recentLeadIds == null || recentLeadIds.size() == 0) {
            return;
        }
        for (String recentLeadId : recentLeadIds) {
            Map<String,Object> leadFields = sfdcServiceImpl.getLead(recentLeadId);
            if (leadFields.containsKey(sfdcConfiguration.getSfdcIdentifierField())) {
                String leadIdentifierFieldValue = (String) leadFields.get(sfdcConfiguration.getSfdcIdentifierField());
                if (leadIdentifierFieldValue == null) {
                    System.out.println("Skipping lead with null identifier field value for field: " + sfdcConfiguration.getSfdcIdentifierField());
                    continue;
                }
                Set<String> foundLeadIds = sfdcServiceImpl.findLeadIdsByIdentifierValue(leadIdentifierFieldValue);
                assertTrue("Should find a single lead for identifier value " + leadIdentifierFieldValue, foundLeadIds.size() == 1);
                assertEquals("Expected Id to be the same", foundLeadIds.iterator().next(), leadFields.get("Id"));
            }
        }
    }

    @Test
    public void testGetLimits() {
        if (!checkCanRunTests()) return;
        Map<String,Object> limits = sfdcServiceImpl.getLimits();
        assertNotNull("Limits object is null, an error occurred !", limits);
    }

    @Test
    public void testCreateOrUpdateAndSyncLead() {
        if (!checkCanRunTests()) return;
        Profile profile = new Profile();
        profile.setItemId(UUID.randomUUID().toString());
        profile.setProperty("email", "test2@jahia.com");
        profile.setProperty("firstName", "Serge");
        String leadId = sfdcServiceImpl.createOrUpdateLead(profile);
        assertNull("The lead creation should fail since we are missing mandatory fields.", leadId);
        profile.setProperty("lastName", "Huber");
        profile.setProperty("company", "Jahia Solutions Group");
        profile.setProperty("phoneNumber", "+41223613424");
        profile.setProperty("jobTitle", "CTO");
        leadId = sfdcServiceImpl.createOrUpdateLead(profile);
        // now let's try to update it.
        profile.setProperty("company", "Jahia Solutions Group SA");
        sfdcServiceImpl.createOrUpdateLead(profile);
        boolean profileUpdated = sfdcServiceImpl.updateProfileFromLead(profile);
        assertTrue("Profile should have been updated since we are reading status field", profileUpdated);
        profile.setProperty("company", "Another value");
        profileUpdated = sfdcServiceImpl.updateProfileFromLead(profile);
        assertTrue("Profile should have been updated since data is not equal", profileUpdated);
        if (leadId != null) {
            sfdcServiceImpl.deleteLead(leadId);
        }
    }

    @Test
    public void testStreaming() throws Exception {
        if (!checkCanRunTests()) return;
        System.out.println("Running streaming client example....");

        sfdcServiceImpl.setupPushListener(sfdcConfiguration.getSfdcChannel(), new ClientSessionChannel.MessageListener() {
            @Override
            public void onMessage(ClientSessionChannel clientSessionChannel, Message message) {
                System.out.println("Received message for channel" + sfdcConfiguration.getSfdcChannel() + ":"+ message);
            }
        });

        System.out.println("Waiting 10 seconds for streamed data from your organization ...");
        int i=0;
        while (i < 10) {
            Thread.sleep(1000);
            i++;
        }

    }

    @Test
    public void testFailedLogin() throws IOException, HttpException {
        if (!checkCanRunTests()) return;
        InputStream configPropertiesStream = SFDCServiceImplTest.class.getClassLoader().getResourceAsStream("org.apache.unomi.sfdc.cfg");
        Properties properties = new Properties();
        properties.load(configPropertiesStream);
        String loginEndpoint = properties.getProperty("sfdc.login.endpoint");
        Properties testProperties = new Properties();
        if (System.getProperty("sfdcProperties") != null) {
            sfdcServiceImpl.logout();
            InputStream testPropertiesInputStream = new FileInputStream(System.getProperty("sfdcProperties"));
            testProperties.load(testPropertiesInputStream);
            if (testProperties.getProperty("sfdc.login.endpoint") != null) {
                loginEndpoint = testProperties.getProperty("sfdc.login.endpoint");
            }
            String userUserName = testProperties.getProperty("sfdc.user.username");
            String userPassword = testProperties.getProperty("sfdc.user.password");
            String userSecurityToken = testProperties.getProperty("sfdc.user.securityToken");
            String consumerKey = testProperties.getProperty("sfdc.consumer.key");
            String consumerSecret = testProperties.getProperty("sfdc.consumer.secret");
            SFDCConfiguration sfdcConfiguration = new SFDCConfiguration();
            sfdcConfiguration.setSfdcLoginEndpoint(loginEndpoint);
            sfdcConfiguration.setSfdcUserUsername(userUserName);
            sfdcConfiguration.setSfdcUserPassword(userPassword + "wrongpassword");
            sfdcConfiguration.setSfdcUserSecurityToken(userSecurityToken);
            sfdcConfiguration.setSfdcConsumerKey(consumerKey);
            sfdcConfiguration.setSfdcConsumerSecret(consumerSecret);
            boolean loginSuccessful = sfdcServiceImpl.login(sfdcConfiguration);
            assertNull("Session should not be valid since we used a wrong password !", sfdcServiceImpl.getSFDCSession());

            // we login properly for other tests to execute properly.
            sfdcConfiguration.setSfdcUserPassword(userPassword);
            loginSuccessful = sfdcServiceImpl.login(sfdcConfiguration);
            assertTrue("Login with proper credentials should have worked !", loginSuccessful);
        }
    }
}
