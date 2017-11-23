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

package org.apache.unomi.mailchimp.services.internal;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.commons.lang.StringUtils;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.unomi.api.Profile;
import org.apache.unomi.api.actions.Action;
import org.apache.unomi.mailchimp.services.HttpUtils;
import org.apache.unomi.mailchimp.services.MailChimpResult;
import org.apache.unomi.mailchimp.services.MailChimpService;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class MailChimpServiceImpl implements MailChimpService {
    private static Logger logger = LoggerFactory.getLogger(MailChimpServiceImpl.class);

    private String apiKey;
    private String urlSubDomain;
    private CloseableHttpClient httpClient;

    @Override
    public List<HashMap<String, String>> getAllLists() {
        List<HashMap<String, String>> mcLists = new ArrayList<>();
        if (isMailChimpConnectorConfigured() && isMailChimpServerOnline()) {
            JsonNode response = HttpUtils.executeGetRequest(httpClient, getBaseUrl() + "/lists", getHeaders());
            if (response != null) {
                if (response.has("lists") && response.get("lists").size() > 0) {
                    for (JsonNode list : response.get("lists")) {
                        if (list.has("id") && list.has("name")) {
                            HashMap<String, String> mcListInfo = new HashMap<>();
                            mcListInfo.put("id", list.get("id").asText());
                            mcListInfo.put("name", list.get("name").asText());
                            mcLists.add(mcListInfo);
                        } else {
                            logger.warn("Missing mandatory information for list, {}", list.asText());
                        }
                    }
                } else {
                    logger.info("No list to return, response was {}", response.asText());
                }
            }
        }
        return mcLists;
    }

    @Override
    public MailChimpResult addToMCList(Profile profile, Action action) {
        if (!isMailChimpConnectorConfigured() || !visitorHasMandatoryProperties(profile)) {
            return MailChimpResult.ERROR;
        }

        String listIdentifier = (String) action.getParameterValues().get("listIdentifier");
        if (StringUtils.isBlank(listIdentifier)) {
            logger.error("MailChimp list identifier not found");
            return MailChimpResult.ERROR;
        }

        JsonNode member = isMemberOfMailChimpList(profile, action);
        if (member != null) {
            if (member.has("status")) {
                if (member.get("status").asText().equals("unsubscribed")) {
                    logger.info("The visitor is already in the list, this status is unsubscribed");
                    JSONObject body = new JSONObject();
                    body.put("status", "subscribed");
                    MailChimpResult response = updateSubscription(listIdentifier, body.toString(), member, true);
                    return updateSubscription(listIdentifier, body.toString(), member, true);
                }
                return MailChimpResult.NO_CHANGE;
            }
        }
        JSONObject nameStruct = new JSONObject();
        nameStruct.put("FNAME", profile.getProperty("firstName").toString());
        nameStruct.put("LNAME", profile.getProperty("lastName").toString());

        JSONObject userData = new JSONObject();
        userData.put("merge_fields", nameStruct);
        userData.put("email_type", "html");
        userData.put("email_address", profile.getProperty("email").toString());
        userData.put("status", "subscribed");

        JSONArray dataMember = new JSONArray();
        dataMember.put(userData);

        JSONObject body = new JSONObject();
        body.put("members", dataMember);


        // BEFORE DOING THAT CHECK IF THE USER IS NOT ALREADY IN THE LIST
        // THEN IF THE USER IS ALREADY IN THE LIST CHECK IF HE IS SUBSCRIBE OR UNSUBSCRIBE
        // IF UNSUBSCRIBE SHOULD WE CHANGE IT TO SUBSCRIBE ?
        JsonNode response = HttpUtils.executePostRequest(httpClient, getBaseUrl() + "/lists/" + listIdentifier, getHeaders(), body.toString());
        if (response != null) {
            if (response.has("errors") && response.get("errors").elements().hasNext() && response.get("errors")
                    .elements().next().has("error")) {
                return MailChimpResult.NO_CHANGE;
            } else {
                return MailChimpResult.UPDATED;
            }
        }
        return MailChimpResult.ERROR;

    }

    @Override
    public MailChimpResult removeFromMCList(Profile profile, Action action) {
        if (!isMailChimpConnectorConfigured() || !visitorHasMandatoryProperties(profile)) {
            return MailChimpResult.ERROR;
        }

        String listIdentifier = (String) action.getParameterValues().get("listIdentifier");
        if (StringUtils.isBlank(listIdentifier)) {
            logger.warn("Couldn't get the list identifier from Unomi");
            return MailChimpResult.ERROR;
        }

        JsonNode member = isMemberOfMailChimpList(profile, action);
        if (member == null) {
            return MailChimpResult.ERROR;
        }
        if (!member.has("id")) {
            return MailChimpResult.NO_CHANGE;
        }


        JsonNode response = HttpUtils.executeDeleteRequest(httpClient, getBaseUrl() + "/lists/" + listIdentifier + "/members/" + member.get("id").asText(), getHeaders());
        if (response == null) {
            logger.error("Couldn't remove the visitor from the list");
            return MailChimpResult.ERROR;
        }
        return MailChimpResult.REMOVED;
    }

    @Override
    public MailChimpResult unsubscribeFromMCList(Profile profile, Action action) {
        if (!isMailChimpConnectorConfigured() || !visitorHasMandatoryProperties(profile)) {
            return MailChimpResult.ERROR;
        }

        String listIdentifier = (String) action.getParameterValues().get("listIdentifier");
        if (StringUtils.isBlank(listIdentifier)) {
            logger.warn("Couldn't get the list identifier from Unomi");
            return MailChimpResult.ERROR;
        }

        JsonNode member = isMemberOfMailChimpList(profile, action);
        if (member == null) {
            logger.error("Visitor was not part of the list");
            return MailChimpResult.ERROR;
        }

        if (member.get("status").asText().equals("unsubscribed")) {
            return MailChimpResult.NO_CHANGE;
        }

        JSONObject body = new JSONObject();
        body.put("status", "unsubscribed");


        return updateSubscription(listIdentifier, body.toString(), member, false);


    }

    private void initHttpClient() {
        if (httpClient == null) {
            httpClient = HttpUtils.initHttpClient();
        }
    }

    private boolean isMailChimpConnectorConfigured() {
        if (StringUtils.isNotBlank(apiKey) && StringUtils.isNotBlank(urlSubDomain)) {
            initHttpClient();
            return true;
        }
        logger.error("MailChimp extension isn't correctly configured, please check cfg file.");
        return false;
    }

    private boolean isMailChimpServerOnline() {
        JsonNode response = HttpUtils.executeGetRequest(httpClient, getBaseUrl() + "/ping", getHeaders());
        if (response != null) {
            if (response.has("health_status") && response.get("health_status").textValue().equals("Everything's Chimpy!")) {
                return true;
            } else {
                logger.error("Error when communicating with MailChimp server, response was: {}", response.asText());
                return false;
            }
        }
        return false;
    }

    private boolean visitorHasMandatoryProperties(Profile profile) {
        if (profile.getProperty("firstName") == null
                || profile.getProperty("lastName") == null
                || profile.getProperty("email") == null) {
            logger.warn("Visitor mandatory properties are missing");
            return false;
        }
        return true;
    }

    private JsonNode isMemberOfMailChimpList(Profile profile, Action action) {
        String listIdentifier = (String) action.getParameterValues().get("listIdentifier");
        if (StringUtils.isBlank(listIdentifier)) {
            logger.warn("MailChimp list identifier not found");
            return null;
        }
        String email = profile.getProperty("email").toString();
        JsonNode response = HttpUtils.executeGetRequest(httpClient, getBaseUrl() + "/lists/" + listIdentifier + "/members/", getHeaders());
        JsonNode member = null;
        if (response != null) {
            if (response.has("members")) {
                if (response.get("members").iterator().hasNext()
                        && response.get("members").iterator().next().has("email_address")) {
                    for (JsonNode m : response.get("members")) {
                        if (m.get("email_address").textValue().equals(email)) {
                            member = m;
                            break;
                        }
                    }
                    if (member == null) {
                        return response;

                    } else {
                        return member;
                    }
                }
            }
            return response;
        }
        return null;
    }

    private MailChimpResult updateSubscription(String listIdentifier, String jsonData, JsonNode member, boolean toSubscribe) {
        JsonNode response = HttpUtils.executePatchRequest(httpClient, getBaseUrl() + "/lists/" + listIdentifier + "/members/" + member.get("id").asText(), getHeaders(), jsonData);
        if (response != null) {
            if (response.has("status")) {
                String responseStatus = response.get("status").asText();
                if ((toSubscribe && responseStatus.equals("subscribed")) || (!toSubscribe && responseStatus.equals("unsubscribed"))) {
                    return MailChimpResult.UPDATED;
                } else {
                    return MailChimpResult.NO_CHANGE;
                }


            }
        }
        logger.error("Couldn't update the subscription of the visitor");
        return MailChimpResult.ERROR;
    }

    private String getBaseUrl() {
        return "https://" + urlSubDomain + ".api.mailchimp.com/3.0";
    }

    private HashMap<String, String> getHeaders() {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("Accept", "application/json");
        headers.put("Authorization", "apikey " + apiKey);
        return headers;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public void setUrlSubDomain(String urlSubDomain) {
        this.urlSubDomain = urlSubDomain;
    }
}
