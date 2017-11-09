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
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.unomi.api.Profile;
import org.apache.unomi.api.actions.Action;
import org.apache.unomi.mailchimp.services.HttpUtils;
import org.apache.unomi.mailchimp.services.MailchimpService;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class MailchimpServiceImpl implements MailchimpService {

    private static Logger logger = LoggerFactory.getLogger(MailchimpServiceImpl.class);
    private String headerAuthorizationPassword;
    private String url;
    private CloseableHttpClient httpClient;
    private HashMap<String, String> headers = new HashMap();
    private JsonNode response;

    public void setHeaderAuthorizationPassword(String headerAuthorizationPassword) {
        this.headerAuthorizationPassword = headerAuthorizationPassword;
    }

    private boolean isConfigured() {
        if (headerAuthorizationPassword != null) {
            logger.info("The extension is correctly configured ");
            return true;
        } else {
            logger.info("The extension isn't correctly configured, please check cfg file ");
            return false;
        }
    }

    /***
     *Initialization of the HttpClient
     * @return httpClient
     */
    private CloseableHttpClient initHttpUtils() {

        if (httpClient == null) {
            httpClient = HttpUtils.initHttpClient();
        }
        return httpClient;
    }

    /***
     * Add the headers request into a HashMap
     * @return headers
     */
    private HashMap<String, String> addHeaders() {
        headers.put("Accept", "application/json");
        headers.put("Authorization", "apikey " + headerAuthorizationPassword);
        return headers;
    }

    /***
     * Build the request Url
     * @return String
     */
    private String urlBuilder() {
        return "https://us16.api.mailchimp.com/3.0/lists";
    }

    /**
     * Build custom Url with parameter
     *
     * @param otherParameter
     * @return String
     */
    private String urlBuilder(String otherParameter) {
        return urlBuilder() + "/" + otherParameter;
    }

    /***
     * Get All lists available on Mailchimp
     * @return List of Mailchimp's list
     */
    @Override
    public List<HashMap<String, String>> executeGetAllLists() {
        if (!isConfigured()) {
            return null;
        }
        initHttpUtils();
        response = executeRequestDoGet();
        return extractInfoListsMc(response);
    }

    /***
     * Execute Get type requests
     * @return JsonNode the response
     */
    private JsonNode executeRequestDoGet() {
        url = urlBuilder();
        addHeaders();
        return HttpUtils.doGetHttp(httpClient, url, headers);
    }

    /**
     * @param rawResponse the response from the API
     * @return Mailchimp's lists with the id and the name.
     */
    private List<HashMap<String, String>> extractInfoListsMc(JsonNode rawResponse) {
        List<HashMap<String, String>> mcLists = new ArrayList<>();
        if (rawResponse.has("lists")) {


            for (JsonNode list : rawResponse.get("lists")) {

                if (list.has("id") && list.has("name")) {
                    HashMap<String, String> mcListInfo = new HashMap<>();
                    mcListInfo.put("id", list.get("id").asText());
                    mcListInfo.put("name", list.get("name").asText());

                    mcLists.add(mcListInfo);
                } else {

                    logger.info("API Response doesn't contains the info" + rawResponse.asText());
                }
            }
        }

        return mcLists;
    }

    /**
     * @param action, the  selected list from the rule is stocked in the action
     * @param profile the current user
     */
    @Override
    public boolean executePostAddToMCList(Profile profile, Action action) {
        initHttpUtils();
        if (!isConfigured()) {
            return false;
        }
        String listIdentifier = (String) action.getParameterValues().get("listIdentifier");
        response = executeRequestDoPost(profile, listIdentifier);
        if (response != null) {
            if (response.has("errors") && response.get("errors").elements().hasNext() && response.get("errors")
                    .elements().next().has("error")) {

                logger.info("Info :" + response.get("errors")
                        .elements().next().get("error"));
                return false;
            } else {

                logger.info("Success User has been inserted");
                return true;
            }
        } else {
            logger.info("Failed :  No Update");
            return false;
        }
    }

    /***
     * Execute Post type requests
     * @param profile  profile info, first name, last name and mail
     * @param urlList list of Mailchimp
     * @return raw response form the API, in JsonNode
     */

    private JsonNode executeRequestDoPost(Profile profile, String urlList) {
        url = urlBuilder(urlList);
        JSONObject body = CreateBodyPostAddMCList(profile);

        // body is null if the visitor has no email or fist name or last name
        if (body != null)
            return HttpUtils.doPostHttp(httpClient, url, headers, body);
        return null;
    }

    /**
     * Create the body which will be sent
     *
     * @param profile the current user
     * @return The body to send to the request
     */
    private JSONObject CreateBodyPostAddMCList(Profile profile) {
        if (profile.getProperty("firstName") == null || profile.getProperty("lastName") == null || profile
                .getProperty("email") == null) {
            logger.info("Error to get Profile's info");
            return null;
        }

        String firstName = profile.getProperty("firstName").toString();
        String lastName = profile.getProperty("lastName").toString();
        String email = profile.getProperty("email").toString();
        addHeaders();

        JSONObject nameStruct = new JSONObject();
        JSONObject userData = new JSONObject();
        JSONArray dataMember = new JSONArray();
        JSONObject dataMembers = new JSONObject();

        nameStruct.put("FNAME", firstName);
        nameStruct.put("LNAME", lastName);
        userData.put("merge_fields", nameStruct);
        userData.put("email_type", "html");
        userData.put("email_address", email);
        userData.put("status", "subscribed");
        dataMember.put(userData);
        dataMembers.put("members", dataMember);

        return dataMembers;
    }
}
