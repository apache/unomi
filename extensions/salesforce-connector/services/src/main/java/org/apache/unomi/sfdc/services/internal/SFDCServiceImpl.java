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


import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.util.ISO8601DateFormat;
import org.apache.http.Header;
import org.apache.http.HttpException;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.*;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.apache.unomi.api.Consent;
import org.apache.unomi.api.Profile;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.apache.unomi.sfdc.services.SFDCConfiguration;
import org.apache.unomi.sfdc.services.SFDCService;
import org.apache.unomi.sfdc.services.SFDCSession;
import org.cometd.bayeux.Channel;
import org.cometd.bayeux.Message;
import org.cometd.bayeux.client.ClientSessionChannel;
import org.cometd.client.BayeuxClient;
import org.cometd.client.transport.ClientTransport;
import org.cometd.client.transport.LongPollingTransport;
import org.eclipse.jetty.client.ContentExchange;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.util.ajax.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.text.DateFormat;
import java.text.ParseException;
import java.util.*;

/**
 * Implementation of the Salesforce connector interface
 */
public class SFDCServiceImpl implements SFDCService {
    private static final Logger logger = LoggerFactory.getLogger(SFDCServiceImpl.class.getName());

    private static final String REST_ENDPOINT_URI = "/services/data/v38.0";
    private static final String STREAMING_ENDPOINT_URI = "/cometd/38.0";

    private static final String RESULTSET_KEY_CONTACT = "CONTACT";
    private static final String RESULTSET_KEY_LEAD = "LEAD";

    private static final int CONNECTION_TIMEOUT = 20 * 1000;  // milliseconds
    private static final int READ_TIMEOUT = 120 * 1000; // milliseconds

    private SFDCConfiguration sfdcConfiguration;
    private SFDCConfiguration defaultSFDCConfiguration;

    private Set<String> sfdcLeadMandatoryFields = new TreeSet<>();
    private Set<String> sfdcLeadUpdateableFields = new TreeSet<>();

    private SFDCSession sfdcSession;
    private DateFormat iso8601DateFormat = new ISO8601DateFormat();

    private PersistenceService persistenceService;

    public void setPersistenceService(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    public void setDefaultSFDCConfiguration(SFDCConfiguration defaultSFDCConfiguration) {
        this.defaultSFDCConfiguration = defaultSFDCConfiguration;
    }

    public SFDCSession getSFDCSession() {
        return sfdcSession;
    }

    @Override
    public SFDCConfiguration loadConfiguration() {
        if (persistenceService == null) {
            return null;
        }
        SFDCConfiguration sfdcConfiguration = persistenceService.load("sfdcConfiguration", SFDCConfiguration.class);
        return sfdcConfiguration;
    }

    @Override
    public boolean saveConfiguration(SFDCConfiguration sfdcConfiguration) {
        if (persistenceService == null) {
            return false;
        }
        boolean result = persistenceService.save(sfdcConfiguration);
        if (result) {
            this.sfdcConfiguration = sfdcConfiguration;
            try {
                if (login(sfdcConfiguration)) {
                    return true;
                }
            } catch (HttpException e) {
                logger.warn("Error trying to login with new configuration {}", sfdcConfiguration, e);
                result = false;
            } catch (IOException e) {
                logger.warn("Error trying to login with new configuration {}", sfdcConfiguration, e);
                result = false;
            }
        } else {
            logger.error("Error trying to save new Salesforce connection configuration !");
        }
        return result;
    }

    public void start() {
        try {
            iso8601DateFormat = new ISO8601DateFormat();

            SFDCConfiguration sfdcConfiguration = loadConfiguration();
            if (sfdcConfiguration != null) {
                this.sfdcConfiguration = sfdcConfiguration;
            } else {
                this.sfdcConfiguration = defaultSFDCConfiguration;
            }

            if (this.sfdcConfiguration.isComplete()) {
                boolean loginSuccessful = login(this.sfdcConfiguration);
                if (!loginSuccessful) {
                    throw new Exception("Login failed");
                }
                sfdcLeadMandatoryFields = getLeadMandatoryFields();
                // setupPushTopics(SFDCSession.getEndPoint(), SFDCSession.getSessionId());
                logger.info("Salesforce connector initialized successfully.");
            } else {
                logger.warn("Salesforce connector is not yet configured.");
            }
        } catch (HttpException | IOException e) {
            logger.error("Failed to init SFDCService properly", e);
        } catch (Exception e) {
            logger.error("Failed to init SFDCService properly", e);
        }
    }

    public void stop() {
    }

    public Set<String> getRecentLeadIds() {
        if (!isConnected()) {
            return null;
        }
        Set<String> recentLeadIds = new LinkedHashSet<>();

        String baseUrl = sfdcSession.getEndPoint() + REST_ENDPOINT_URI + "/sobjects/Lead";
        HttpGet getRecentLeads = new HttpGet(baseUrl);

        try {
            Object responseObject = handleRequest(getRecentLeads);
            if (responseObject == null) {
                logger.warn("Couldn't retrieve recent leads");
                return null;
            }
            Map<String, Object> queryResponse = (Map<String, Object>) responseObject;
            if (queryResponse.containsKey("recentItems")) {
                logger.debug("Response received from Salesforce: {}", queryResponse);
                Object[] recentItems = (Object[]) queryResponse.get("recentItems");
                for (Object recentItem : recentItems) {
                    Map<String, String> recentItemMap = (Map<String, String>) recentItem;
                    recentLeadIds.add(recentItemMap.get("Id"));
                }
            }

        } catch (IOException e) {
            logger.error("Error getting recent leads", e);
        } catch (HttpException e) {
            logger.error("Error getting recent leads", e);
        }

        return recentLeadIds;
    }

    public Map<String, Object> getSObject(String sobjectName, String objectId) {
        if (!isConnected()) {
            return null;
        }
        Map<String, Object> sobjectMap = new LinkedHashMap<>();

        String baseUrl = sfdcSession.getEndPoint() + REST_ENDPOINT_URI + "/sobjects/" + sobjectName + "/" + objectId;
        HttpGet getSObject = new HttpGet(baseUrl);

        try {
            Object responseObject = handleRequest(getSObject);
            if (responseObject == null) {
                logger.warn("Couldn't retrieve sobject {} with id {}", sobjectName, objectId);
                return null;
            }
            Map<String, Object> queryResponse = (Map<String, Object>) responseObject;
            if (queryResponse != null) {
                logger.debug("Response received from Salesforce: {}", queryResponse);
                sobjectMap = new LinkedHashMap<>(queryResponse);
            }

        } catch (IOException e) {
            logger.error("Error getting sobject {} with id {}", sobjectName, objectId, e);
        } catch (HttpException e) {
            logger.error("Error getting sobject {} with id {}", sobjectName, objectId, e);
        }
        return sobjectMap;
    }

    public Map<String, Object> getSObjectDescribe(String sobjectName) {
        Map<String, Object> sobjectDescribe = new LinkedHashMap<>();
        if (!isConnected()) {
            return null;
        }

        String baseUrl = sfdcSession.getEndPoint() + REST_ENDPOINT_URI + "/sobjects/" + sobjectName + "/describe";
        HttpGet getSObjectDescribe = new HttpGet(baseUrl);

        try {
            Object responseObject = handleRequest(getSObjectDescribe);
            if (responseObject == null) {
                logger.warn("Couldn't retrieve sobject {} describe", sobjectName);
                return null;
            }
            Map<String, Object> queryResponse = (Map<String, Object>) responseObject;
            if (queryResponse != null) {
                logger.debug("Response received from Salesforce: {}", queryResponse);
                sobjectDescribe = new LinkedHashMap<>(queryResponse);
            }

        } catch (IOException e) {
            logger.error("Error getting sobject {}", sobjectName, e);
        } catch (HttpException e) {
            logger.error("Error getting sobject {}", sobjectName, e);
        }
        return sobjectDescribe;
    }

    public Map<String, Object> getLead(String leadId) {
        return getSObject("Lead", leadId);
    }

    public Set<String> getLeadMandatoryFields() {
        Set<String> mandatoryFields = new TreeSet<>();
        if (!isConnected()) {
            return null;
        }
        Map<String, Object> leadDescribe = getSObjectDescribe("Lead");
        Object[] fields = (Object[]) leadDescribe.get("fields");
        Set<String> updateableFields = new TreeSet<>();
        Set<String> compoundFieldNames = new TreeSet<>();
        for (Object field : fields) {
            Map<String, Object> fieldDescribe = (Map<String, Object>) field;
            String fieldName = (String) fieldDescribe.get("name");
            String compoundFieldName = (String) fieldDescribe.get("compoundFieldName");
            if (compoundFieldName != null) {
                compoundFieldNames.add(compoundFieldName);
            }
            String fieldType = (String) fieldDescribe.get("type");
            Boolean fieldUpdateable = (Boolean) fieldDescribe.get("updateable");
            Boolean fieldCreateable = (Boolean) fieldDescribe.get("createable");
            Boolean fieldDefaultedOnCreate = (Boolean) fieldDescribe.get("defaultedOnCreate");
            Boolean fieldNillable = (Boolean) fieldDescribe.get("nillable");
            if (fieldUpdateable) {
                updateableFields.add(fieldName);
            }
            if (!fieldNillable && !fieldDefaultedOnCreate) {
                mandatoryFields.add(fieldName);
            }
        }
        mandatoryFields.removeAll(compoundFieldNames);
        updateableFields.removeAll(compoundFieldNames);
        sfdcLeadUpdateableFields = updateableFields;
        return mandatoryFields;
    }

    public boolean deleteLead(String leadId) {
        if (!isConnected()) {
            return false;
        }
        String baseUrl = sfdcSession.getEndPoint() + REST_ENDPOINT_URI + "/sobjects/Lead/" + leadId;
        HttpDelete deleteLead = new HttpDelete(baseUrl);
        try {
            Object responseObject = handleRequest(deleteLead);
        } catch (IOException e) {
            logger.error("Error deleting lead {}", leadId, e);
        } catch (HttpException e) {
            logger.error("Error deleting lead {}", leadId, e);
        }
        return true;
    }

    private Set<String> mappingResponse(Object response, Set<String> results) {
        Map<String, Object> result = (Map<String, Object>) response;
        Long totalSize = (Long) result.get("totalSize");
        Boolean done = (Boolean) result.get("done");
        Object[] recordObjects = (Object[]) result.get("records");
        if (totalSize == null || totalSize < 1) {
            return results;
        }
        for (Object recordObject : recordObjects) {
            Map<String, Object> record = (Map<String, Object>) recordObject;
            if (record.containsKey("Id")) {
                results.add((String) record.get("Id"));
            }
        }
        return results;
    }

    public Set<String> findLeadIdsByIdentifierValue(String identifierFieldValue) {
        Set<String> results = new LinkedHashSet<>();
        if (!isConnected()) {
            return results;
        }
        Object response = query("SELECT Id FROM Lead WHERE " + sfdcConfiguration.getSfdcIdentifierField() + "='" +
                identifierFieldValue + "'");
        if (response == null) {
            return results;
        }
        return mappingResponse(response, results);
    }

    private boolean isProfileInContacts(String identifierFieldValue) {
        if (sfdcConfiguration.isSfdcCheckIfContactExistBeforeLeadCreation()) {
            logger.info("Checking if we have a contact for identifier value {}...", identifierFieldValue);
            Object response;
            Set<String> queryResult = new LinkedHashSet<>();
            response = query("SELECT Id FROM Contact WHERE " + sfdcConfiguration.getSfdcIdentifierField() +
                    "='" + identifierFieldValue + "'");
            queryResult = mappingResponse(response, queryResult);
            if (queryResult.size() > 0) {
                return true;
            }
        }
        return false;
    }

    private boolean isMappingConsent(Profile profile, Map<String, Object> sfdcLeadFields) {
        Map<String, Consent> consents = profile.getConsents();
        String mappingConsentsString = sfdcConfiguration.getSfdcFieldsConsents();
        if (mappingConsentsString.isEmpty()) {
            return false;
        }
        String[] mappingConsents = mappingConsentsString.split(",");
        if (mappingConsents.length <= 0) {
            logger.error("Error with the mapping field {} please check the cfg file", mappingConsentsString);
            return false;
        }
        boolean isPerfectlyMapped = true;
        for (String oneMappingConsent : mappingConsents) {
            String[] oneRawMappingConsent = oneMappingConsent.split(":");

            if (oneRawMappingConsent.length <= 0) {
                logger.error("Error with the mapping field {} please check the cfg file", mappingConsentsString);
                isPerfectlyMapped = false;
            } else {
                if (consents.get(oneRawMappingConsent[0]) == null) {
                    logger.warn("Consent {} not found or didn't answer yet", oneRawMappingConsent[0]);
                    isPerfectlyMapped = false;
                }
                if (isPerfectlyMapped) {
                    sfdcLeadFields.put(oneRawMappingConsent[1], consents.get(oneRawMappingConsent[0]).getStatus().toString());
                    logger.info("Consent {} was mapped with {}", oneRawMappingConsent[0], oneRawMappingConsent[1]);
                }
            }
        }
        return isPerfectlyMapped;
    }

    @Override
    public String createOrUpdateLead(Profile profile) {
        if (!isConnected()) {
            return null;
        }
        // first we must check if an existing contact exists for the profile.
        String unomiIdentifierValue = (String) profile.getProperty(sfdcConfiguration.getUnomiIdentifierField());
        if (isProfileInContacts(unomiIdentifierValue)) {
            logger.info("Contact {}  found in SFDC... No SFDC field value to send, will not send anything to " +
                    "Salesforce. ", unomiIdentifierValue);
            return null;
        }
        // then we must check if an existing lead exists for the profile.
        logger.info("Checking if we have a lead for identifier value {}...", unomiIdentifierValue);
        Set<String> foundExistingSfdcLeadIds = findLeadIdsByIdentifierValue(unomiIdentifierValue);

        Map<String, Object> sfdcLeadFields = new HashMap<>();
        Map<String, Object> existingSfdcLeadFields = new HashMap<>();
        Date sfdcLastModified = null;

        if (foundExistingSfdcLeadIds.size() > 1) {
            // we found multiple leads matching the identifier value !
            logger.warn("Found multiple matching leads for identifier value {}, will use first matching one !",
                    unomiIdentifierValue);
        }

        if (foundExistingSfdcLeadIds.size() > 0) {
            logger.info("Found an existing lead, attempting to update it...");
            // we found an existing lead we must update it
            existingSfdcLeadFields = getLead(foundExistingSfdcLeadIds.iterator().next());
            if (existingSfdcLeadFields.get("LastModifiedDate") != null) {
                try {
                    sfdcLastModified = iso8601DateFormat.parse((String) existingSfdcLeadFields.get("LastModifiedDate"));
                } catch (ParseException e) {
                    logger.error("Error parsing date {}", existingSfdcLeadFields.get("LastModifiedDate"), e);
                }
            }
        } else {
            logger.info("No existing lead found.");
        }

        for (String profilePropertyKey : profile.getProperties().keySet()) {
            String sfdcFieldName = sfdcConfiguration.getUnomiToSfdcFieldMappings().get(profilePropertyKey);
            if (sfdcFieldName == null) {
                // we skip unmapped fields
                continue;
            }
            Object unomiPropertyValue = profile.getProperties().get(profilePropertyKey);
            if (existingSfdcLeadFields.get(sfdcFieldName) == null) {
                // we only set the field if it didn't have a value.
                logger.info("Setting SFDC field {} value to {}", sfdcFieldName, unomiPropertyValue);
                sfdcLeadFields.put(sfdcFieldName, unomiPropertyValue);
            } else {
                // current strategy : Unomi field value wins if different from Salesforce value
                // @todo we should probably improve this by tracking last modification dates on profile/lead properties
                Object sfdcLeadFieldValue = existingSfdcLeadFields.get(sfdcFieldName);
                if (!unomiPropertyValue.equals(sfdcLeadFieldValue)) {
                    logger.info("Overwriting SFDC field {} value to {}", sfdcFieldName, unomiPropertyValue);
                    sfdcLeadFields.put(sfdcFieldName, unomiPropertyValue);
                }
            }
        }
        if (isMappingConsent(profile, sfdcLeadFields)) {
            logger.warn("Ok Well Done");
        } else {
            logger.warn("The consents mapping went wrong");
        }


        if (sfdcLeadFields.size() == 0) {
            logger.info("No SFDC field value to send, will not send anything to Salesforce.");
            if (foundExistingSfdcLeadIds.size() == 0) {
                return null;
            } else {
                return foundExistingSfdcLeadIds.iterator().next();
            }
        }

        if (existingSfdcLeadFields.size() == 0) {
            // if we are creating a lead, let's make sure we have all the mandatory fields before sending the request
            boolean missingMandatoryFields = false;
            for (String leadMandatoryFieldName : sfdcLeadMandatoryFields) {
                if (sfdcLeadFields.get(leadMandatoryFieldName) == null) {
                    logger.warn("Missing mandatory field {}, aborting sending to Salesforce", leadMandatoryFieldName);
                    missingMandatoryFields = true;
                }
            }
            if (missingMandatoryFields) {
                return null;
            }
        }

        String baseUrl = sfdcSession.getEndPoint() + REST_ENDPOINT_URI + "/sobjects/Lead";
        HttpEntityEnclosingRequestBase request = new HttpPost(baseUrl);
        if (foundExistingSfdcLeadIds.size() > 0) {
            baseUrl = sfdcSession.getEndPoint() + REST_ENDPOINT_URI + "/sobjects/Lead/" + foundExistingSfdcLeadIds
                    .iterator().next();
            sfdcLeadFields.remove("Id");
            request = new HttpPatch(baseUrl);
        }

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            StringEntity requestEntity = new StringEntity(
                    objectMapper.writeValueAsString(sfdcLeadFields),
                    ContentType.APPLICATION_JSON);
            request.setEntity(requestEntity);
            Object responseObject = handleRequest(request);
            if (responseObject == null) {
                return null;
            }
            if (responseObject instanceof Map) {
                Map<String, Object> responseData = (Map<String, Object>) responseObject;
                if (responseData.get("id") != null) {
                    String sfdcId = (String) responseData.get("id");
                    logger.info("Lead successfully created/updated in Salesforce. sfdcId={}", sfdcId);
                    return sfdcId;
                }
            }
            logger.info("Response received from Salesforce: {}", responseObject);
        } catch (IOException e) {
            logger.error("Error creating or updating lead for profile {}", profile, e);
        } catch (HttpException e) {
            logger.error("Error creating or updating lead for profile {}", profile, e);
        }

        if (foundExistingSfdcLeadIds.size() == 0) {
            return null;
        } else {
            return foundExistingSfdcLeadIds.iterator().next();
        }
    }

    @Override
    public boolean updateProfileFromLead(Profile profile) {
        if (!isConnected()) {
            return false;
        }
        String unomiIdentifierValue = (String) profile.getProperty(sfdcConfiguration.getUnomiIdentifierField());
        Set<String> foundSfdcLeadIds = findLeadIdsByIdentifierValue(unomiIdentifierValue);
        if (foundSfdcLeadIds.size() == 0) {
            logger.info("No lead found in Salesforce corresponding to profile {}", profile);
            // we didn't find a corresponding lead in salesforce.
            return false;
        } else if (foundSfdcLeadIds.size() > 1) {
            logger.warn("Found multiple leads in Salesforce for identifier value {}, will use first one.",
                    foundSfdcLeadIds);
        } else {
            logger.info("Found corresponding lead with identifier value {}", unomiIdentifierValue);
        }
        Map<String, Object> sfdcLead = getLead(foundSfdcLeadIds.iterator().next());
        if (sfdcLead == null) {
            logger.error("Error retrieving lead {} from Salesforce", foundSfdcLeadIds);
            return false;
        }
        boolean profileUpdated = false;
        for (Map.Entry<String, String> sfdcToUnomiFieldMappingEntry : sfdcConfiguration.getSfdcToUnomiFieldMappings()
                .entrySet()) {
            String sfdcFieldName = sfdcToUnomiFieldMappingEntry.getKey();
            String unomiFieldName = sfdcToUnomiFieldMappingEntry.getValue();
            if (sfdcLead.get(sfdcFieldName) != null) {
                Object sfdcFieldValue = sfdcLead.get(sfdcFieldName);
                if (sfdcFieldValue != null && !sfdcFieldValue.equals(profile.getProperty(unomiFieldName))) {
                    profile.setProperty(unomiFieldName, sfdcFieldValue);
                    profileUpdated = true;
                }
            }
        }
        logger.info("Updated profile {} from Salesforce lead {}", profile, sfdcLead);
        return profileUpdated;
    }

    @Override
    public Map<String, Object> query(String query) {
        if (!isConnected()) {
            return null;
        }
        // first we must check if an existing lead exists for the profile.

        String baseUrl = null;
        try {
            baseUrl = sfdcSession.getEndPoint() + REST_ENDPOINT_URI + "/query?q=" + URLEncoder.encode(query, "UTF-8");
            HttpGet get = new HttpGet(baseUrl);

            Object responseObject = handleRequest(get);
            if (responseObject == null) {
                return null;
            }
            if (responseObject != null && responseObject instanceof Map) {
                return (Map<String, Object>) responseObject;
            }
            return null;
        } catch (UnsupportedEncodingException e) {
            logger.error("Error executing query {}", query, e);
            return null;
        } catch (ClientProtocolException e) {
            logger.error("Error executing query {}", query, e);
            return null;
        } catch (IOException e) {
            logger.error("Error executing query {}", query, e);
            return null;
        } catch (HttpException e) {
            logger.error("Error executing query {}", query, e);
            return null;
        }
    }

    @Override
    public Map<String, Object> getLimits() {
        if (!isConnected()) {
            return null;
        }
        String baseUrl = null;
        try {
            baseUrl = sfdcSession.getEndPoint() + REST_ENDPOINT_URI + "/limits";
            HttpGet get = new HttpGet(baseUrl);

            Object responseObject = handleRequest(get);
            if (responseObject == null) {
                return null;
            }

            if (responseObject instanceof Map) {
                return (Map<String, Object>) responseObject;
            }
            return null;
        } catch (UnsupportedEncodingException e) {
            logger.error("Error retrieving Salesforce API Limits", e);
            return null;
        } catch (ClientProtocolException e) {
            logger.error("Error retrieving Salesforce API Limits", e);
            return null;
        } catch (IOException e) {
            logger.error("Error retrieving Salesforce API Limits", e);
            return null;
        } catch (HttpException e) {
            logger.error("Error retrieving Salesforce API Limits", e);
            return null;
        }
    }

    private BayeuxClient makeClient() throws Exception {
        HttpClient httpClient = new HttpClient();
        httpClient.setConnectTimeout(CONNECTION_TIMEOUT);
        httpClient.setTimeout(READ_TIMEOUT);
        httpClient.start();

        if (sfdcSession == null) {
            logger.error("Invalid session !");
            return null;
        }
        logger.info("Login successful!\nServer URL: " + sfdcSession.getEndPoint()
                + "\nSession ID=" + sfdcSession.getSessionId());

        Map<String, Object> options = new HashMap<String, Object>();
        options.put(ClientTransport.TIMEOUT_OPTION, READ_TIMEOUT);
        LongPollingTransport transport = new LongPollingTransport(
                options, httpClient) {

            @Override
            protected void customize(ContentExchange exchange) {
                super.customize(exchange);
                exchange.addRequestHeader("Authorization", "OAuth " + sfdcSession.getSessionId());
            }
        };

        BayeuxClient client = new BayeuxClient(getSalesforceStreamingEndpoint(
                sfdcSession.getEndPoint()), transport);
        return client;
    }

    public void setupPushListener(String channelName, ClientSessionChannel.MessageListener messageListener) throws
            Exception {
        if (!isConnected()) {
            return;
        }
        final BayeuxClient client = makeClient();
        if (client == null) {
            throw new Exception("Login failed !");
        }
        client.getChannel(Channel.META_HANDSHAKE).addListener
                (new ClientSessionChannel.MessageListener() {
                    @Override
                    public void onMessage(ClientSessionChannel channel, Message message) {

                        logger.debug("[CHANNEL:META_HANDSHAKE]: " + message);

                        boolean success = message.isSuccessful();
                        if (!success) {
                            String error = (String) message.get("error");
                            if (error != null) {
                                logger.error("Error during HANDSHAKE: " + error);
                            }

                            Exception exception = (Exception) message.get("exception");
                            if (exception != null) {
                                logger.error("Exception during HANDSHAKE: ", exception);
                            }
                        }
                    }

                });

        client.getChannel(Channel.META_CONNECT).addListener(
                new ClientSessionChannel.MessageListener() {
                    public void onMessage(ClientSessionChannel channel, Message message) {

                        logger.debug("[CHANNEL:META_CONNECT]: " + message);

                        boolean success = message.isSuccessful();
                        if (!success) {
                            String error = (String) message.get("error");
                            if (error != null) {
                                logger.error("Error during CONNECT: " + error);
                            }
                        }
                    }

                });

        client.getChannel(Channel.META_SUBSCRIBE).addListener(
                new ClientSessionChannel.MessageListener() {

                    public void onMessage(ClientSessionChannel channel, Message message) {

                        logger.debug("[CHANNEL:META_SUBSCRIBE]: " + message);
                        boolean success = message.isSuccessful();
                        if (!success) {
                            String error = (String) message.get("error");
                            if (error != null) {
                                logger.error("Error during SUBSCRIBE: " + error);
                            }
                        }
                    }
                });

        client.handshake();
        logger.debug("Waiting for handshake");

        boolean handshaken = client.waitFor(10 * 1000, BayeuxClient.State.CONNECTED);
        if (!handshaken) {
            logger.error("Failed to handshake: " + client);
        }

        logger.info("Subscribing for channel: " + channelName);

        client.getChannel(channelName).subscribe(messageListener);

    }

    private String getSalesforceStreamingEndpoint(String endpoint) throws MalformedURLException {
        return new URL(endpoint + STREAMING_ENDPOINT_URI).toExternalForm();
    }

    private void setupPushTopics(String host, String sessionId) throws HttpException, IOException {

        String baseUrl = host + REST_ENDPOINT_URI + "/query?q=" + URLEncoder.encode("SELECT Id from PushTopic WHERE " +
                "name = 'LeadUpdates'", "UTF-8");
        HttpGet get = new HttpGet(baseUrl);

        Map<String, String> queryResponse = (Map<String, String>) handleRequest(get);

        if (queryResponse != null && queryResponse.containsKey("count")) {
            logger.info("Push topics setup successfully");
        }
    }

    public boolean login(SFDCConfiguration sfdcConfiguration)
            throws HttpException, IOException {
        String baseUrl = sfdcConfiguration.getSfdcLoginEndpoint() + "/services/oauth2/token";
        HttpPost oauthPost = new HttpPost(baseUrl);
        List<BasicNameValuePair> parametersBody = new ArrayList<>();
        parametersBody.add(new BasicNameValuePair("grant_type", "password"));
        parametersBody.add(new BasicNameValuePair("username", sfdcConfiguration.getSfdcUserUsername()));
        parametersBody.add(new BasicNameValuePair("password", sfdcConfiguration.getSfdcUserPassword() +
                sfdcConfiguration.getSfdcUserSecurityToken()));
        parametersBody.add(new BasicNameValuePair("client_id", sfdcConfiguration.getSfdcConsumerKey()));
        parametersBody.add(new BasicNameValuePair("client_secret", sfdcConfiguration.getSfdcConsumerSecret()));
        oauthPost.setEntity(new UrlEncodedFormEntity(parametersBody, "UTF-8"));

        Map<String, String> oauthLoginResponse = (Map<String, String>) handleRequest(oauthPost, 0, false);
        if (oauthLoginResponse == null) {
            return false;
        }

        sfdcSession = new SFDCSession(
                oauthLoginResponse.get("access_token"),
                oauthLoginResponse.get("instance_url"),
                oauthLoginResponse.get("signature"),
                oauthLoginResponse.get("id"),
                oauthLoginResponse.get("token_type"),
                oauthLoginResponse.get("issued_at"),
                sfdcConfiguration.getSfdcSessionTimeout());
        return true;
    }

    public void logout() {
        sfdcSession = null;
    }

    private SFDCSession getValidSession() {
        if (isSessionValid()) {
            return sfdcSession;
        }
        boolean loginSuccessful = false;
        try {
            loginSuccessful = login(sfdcConfiguration);
            if (loginSuccessful && sfdcSession != null) {
                return sfdcSession;
            }
        } catch (HttpException e) {
            logger.error("Error logging in", e);
            return null;
        } catch (IOException e) {
            logger.error("Error logging in", e);
            return null;
        }
        return null;
    }

    private boolean isSessionValid() {
        if (sfdcSession == null) {
            return false;
        }
        if (sfdcSession.isExpired()) {
            return false;
        }
        return true;
    }

    private Object handleRequest(HttpUriRequest request) throws IOException, HttpException {
        return handleRequest(request, 1, true);
    }

    private Object handleRequest(HttpUriRequest request, int retryCount, boolean addAuthorizationHeader) throws
            IOException, HttpException {
        CloseableHttpClient client = HttpClientBuilder.create().build();
        if (addAuthorizationHeader) {
            SFDCSession sfdcSession = getValidSession();
            if (sfdcSession == null) {
                logger.error("Couldn't get a valid session !");
                return null;
            }
            if (request.containsHeader("Authorization")) {
                logger.debug("Replacing existing authorization header with an updated one.");
                Header[] authorizationHeaders = request.getHeaders("Authorization");
                for (Header authorizationHeader : authorizationHeaders) {
                    request.removeHeader(authorizationHeader);
                }
            }
            request.addHeader("Authorization", "Bearer " + sfdcSession.getSessionId());
        }

        CloseableHttpResponse response = client.execute(request);
        if (response.getStatusLine().getStatusCode() >= 400) {
            if ((response.getStatusLine().getStatusCode() == 401 || response.getStatusLine().getStatusCode() == 403)
                    && retryCount > 0) {
                // probably the session has expired, let's try to login again
                logger.warn("Unauthorized request, attempting to login again...");
                boolean loginSuccessful = login(sfdcConfiguration);
                if (!loginSuccessful) {
                    logger.error("Login failed, cannot execute request {}", request);
                    return null;
                }
                logger.warn("Retrying request {} once again...", request);
                return handleRequest(request, 0, true);
            } else {
                logger.error("Error executing request {}: {}-{}", request, response.getStatusLine().getStatusCode(),
                        response.getStatusLine().getStatusCode());
                if (response.getEntity() != null) {
                    logger.error("Entity={}", EntityUtils.toString(response.getEntity()));
                }
            }
            return null;
        }
        if (response.getEntity() == null) {
            return null;
        }
        return JSON.parse(EntityUtils.toString(response.getEntity()));
    }

    public boolean isConfigured() {
        if (!sfdcConfiguration.isComplete()) {
            logger.warn("Connection to Salesforce is not properly configured !");
            return false;
        }
        return true;
    }

    public boolean isConnected() {
        if (!isConfigured()) {
            return false;
        }
        if (sfdcSession == null) {
            logger.warn("Not connected to SalesForce, operation will not execute.");
            return false;
        } else {
            if (sfdcSession.isExpired()) {
                logger.warn("Connection to Salesforce has expired, will reconnect on next request");
                return true;
            }
        }
        return true;
    }

}
