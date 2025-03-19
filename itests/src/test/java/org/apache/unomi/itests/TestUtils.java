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
 * limitations under the License
 */

package org.apache.unomi.itests;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.ContentType;
import org.apache.http.util.EntityUtils;
import org.apache.unomi.api.*;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.services.DefinitionsService;
import org.apache.unomi.api.services.ScopeService;
import org.apache.unomi.itests.tools.httpclient.HttpClientThatWaitsForUnomi;
import org.apache.unomi.persistence.spi.CustomObjectMapper;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.junit.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class TestUtils {
	private static final String JSON_MYME_TYPE = "application/json";
	private final static Logger LOGGER = LoggerFactory.getLogger(TestUtils.class);

	/**
	 * Retrieves and deserializes a resource from an HTTP response.
	 * Converts the JSON response body into the specified class type.
	 *
	 * @param response The HTTP response containing the resource
	 * @param clazz The class type to deserialize the resource into
	 * @return The deserialized resource object, or null if the response or entity is null
	 * @throws IOException if there is an error reading or parsing the response
	 */
	public static <T> T retrieveResourceFromResponse(HttpResponse response, Class<T> clazz) throws IOException {
		if (response == null) {
			return null;
		}
		if (response.getEntity() == null) {
			return null;
		}
		String jsonFromResponse = EntityUtils.toString(response.getEntity());
		ObjectMapper mapper = CustomObjectMapper.getObjectMapper();
		try {
			T value = mapper.readValue(jsonFromResponse, clazz);
			return value;
		} catch (Throwable t) {
			LOGGER.error("Error parsing response JSON", t);
			t.printStackTrace();
		}
		return null;
	}

	public static RequestResponse executeContextJSONRequest(HttpUriRequest request, String sessionId) throws IOException {
		return executeContextJSONRequest(request, sessionId, -1, true);
	}

	/**
	 * Executes a JSON request to the context service and processes the response.
	 * Validates the response MIME type and handles cookies if a session ID is provided.
	 *
	 * @param request The HTTP request to execute
	 * @param sessionId The session ID to use for cookie handling, or null if not needed
	 * @param expectedStatusCode The expected status code of the response, or -1 if not needed	
	 * @param withAuth Whether to include authentication headers in the request
	 * @return A RequestResponse object containing the response details
	 * @throws IOException if there is an error executing the request or processing the response
	 */
	public static RequestResponse executeContextJSONRequest(HttpUriRequest request, String sessionId, int expectedStatusCode, boolean withAuth) throws IOException {
		try (CloseableHttpResponse response = HttpClientThatWaitsForUnomi.doRequest(request, expectedStatusCode, withAuth)) {
			// validate mimeType
			HttpEntity entity = response.getEntity();
			String mimeType = ContentType.getOrDefault(entity).getMimeType();
			if (expectedStatusCode < 0 || expectedStatusCode < 300) {
				if (!JSON_MYME_TYPE.equals(mimeType)) {
					String entityContent = EntityUtils.toString(entity);
					LOGGER.warn("Invalid response: " + entityContent);
				}
				Assert.assertEquals("Response content type should be " + JSON_MYME_TYPE, JSON_MYME_TYPE, mimeType);
			}

			// get response
			String cookieHeader = null;
			if (sessionId != null) {
				Header setCookieHeader = response.getFirstHeader("Set-Cookie");
				if (setCookieHeader != null) {
					cookieHeader = setCookieHeader.getValue();
				}
			}

			String responseContent = EntityUtils.toString(entity);
			int responseCode = response.getStatusLine().getStatusCode();

			ContextResponse contextResponse = null;
			if (responseCode == 200) {
				contextResponse = CustomObjectMapper.getObjectMapper().readValue(responseContent, ContextResponse.class);
			}

			return new RequestResponse(cookieHeader, responseCode, contextResponse);
		}
	}

	/**
	 * Executes a JSON request to the context service without session handling.
	 * Convenience method that calls executeContextJSONRequest with a null session ID.
	 *
	 * @param request The HTTP POST request to execute
	 * @return A RequestResponse object containing the response details
	 * @throws IOException if there is an error executing the request or processing the response
	 */
	public static RequestResponse executeContextJSONRequest(HttpPost request) throws IOException {
		return executeContextJSONRequest(request, null);
	}

	/**
	 * Removes all profiles from the persistence service.
	 * Creates and executes a query to delete all items of type 'profile'.
	 *
	 * @param definitionsService The service providing condition type definitions
	 * @param persistenceService The service handling data persistence
	 * @return true if the removal was successful, false otherwise
	 */
	public static boolean removeAllProfiles(DefinitionsService definitionsService, PersistenceService persistenceService) {
		Condition condition = new Condition(definitionsService.getConditionType("profilePropertyCondition"));
		condition.setParameter("propertyName","itemType");
		condition.setParameter("comparisonOperator","equals");
		condition.setParameter("propertyValue","profile");

		return persistenceService.removeByQuery(condition, Profile.class);
	}

	/**
	 * Removes all events from the persistence service.
	 * Creates and executes a query to delete all items of type 'event'.
	 *
	 * @param definitionsService The service providing condition type definitions
	 * @param persistenceService The service handling data persistence
	 * @return true if the removal was successful, false otherwise
	 */
	public static boolean removeAllEvents(DefinitionsService definitionsService, PersistenceService persistenceService) {
		Condition condition = new Condition(definitionsService.getConditionType("eventPropertyCondition"));
		condition.setParameter("propertyName","itemType");
		condition.setParameter("comparisonOperator","equals");
		condition.setParameter("propertyValue","event");

		return persistenceService.removeByQuery(condition, Event.class);
	}

	/**
	 * Removes all sessions from the persistence service.
	 * Creates and executes a query to delete all items of type 'session'.
	 *
	 * @param definitionsService The service providing condition type definitions
	 * @param persistenceService The service handling data persistence
	 * @return true if the removal was successful, false otherwise
	 */
	public static boolean removeAllSessions(DefinitionsService definitionsService, PersistenceService persistenceService) {
		Condition condition = new Condition(definitionsService.getConditionType("sessionPropertyCondition"));
		condition.setParameter("propertyName","itemType");
		condition.setParameter("comparisonOperator","equals");
		condition.setParameter("propertyValue","session");

		return persistenceService.removeByQuery(condition, Session.class);
	}

	/**
	 * Creates a new scope in the scope service.
	 * Initializes a scope with the provided ID and name, and saves it to the service.
	 *
	 * @param scopeId The unique identifier for the scope
	 * @param scopeName The display name for the scope
	 * @param scopeService The service to save the scope to
	 */
	public static void createScope(String scopeId, String scopeName, ScopeService scopeService) {
		Scope scope = new Scope();
		scope.setItemId(scopeId);
		Metadata metadata = new Metadata();
		metadata.setName(scopeName);
		metadata.setId(scopeId);
		scope.setMetadata(metadata);
		scopeService.save(scope);
	}

	public static class RequestResponse {
		private ContextResponse contextResponse;
		private String cookieHeaderValue;
		int statusCode;

		public RequestResponse(String cookieHeaderValue, int statusCode, ContextResponse contextResponse) {
			this.cookieHeaderValue = cookieHeaderValue;
			this.statusCode = statusCode;
			this.contextResponse = contextResponse;
		}

		public ContextResponse getContextResponse() {
			return contextResponse;
		}

		public String getCookieHeaderValue() {
			return cookieHeaderValue;
		}

		public int getStatusCode() {
			return statusCode;
		}
	}
}
