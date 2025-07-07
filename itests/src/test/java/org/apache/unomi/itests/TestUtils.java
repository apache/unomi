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
import org.apache.unomi.api.services.ExecutionContextManager;
import org.apache.unomi.api.tenants.Tenant;
import org.apache.unomi.api.tenants.TenantService;
import org.apache.unomi.itests.tools.httpclient.HttpClientThatWaitsForUnomi;
import org.apache.unomi.persistence.spi.CustomObjectMapper;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.junit.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class TestUtils {
	private static final String JSON_MYME_TYPE = "application/json";
	private final static Logger LOGGER = LoggerFactory.getLogger(TestUtils.class);
	private static final int DEFAULT_TRYING_TIMEOUT = 5000; // 5 seconds
	private static final int DEFAULT_TRYING_TRIES = 10;

	/**
	 * Retrieves and deserializes a resource from an HTTP response.
	 * Converts the JSON response body into the specified class type.
	 *
	 * @param <T> The type of object to deserialize into
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

	/**
	 * Executes a JSON request to the context service and processes the response.
	 * Validates the response MIME type and handles cookies if a session ID is provided.
	 *
	 * @param request The HTTP request to execute
	 * @param sessionId The session ID to use for cookie handling, or null if not needed
	 * @return A RequestResponse object containing the response details
	 * @throws IOException if there is an error executing the request or processing the response
	 */
	public static RequestResponse executeContextJSONRequest(HttpUriRequest request, String sessionId) throws IOException {
		return executeContextJSONRequest(request, sessionId, -1, true);
	}

	/**
	 * Executes a JSON request to the context service and processes the response.
	 * Validates the response MIME type, status code, and handles cookies if a session ID is provided.
	 *
	 * @param request The HTTP request to execute
	 * @param sessionId The session ID to use for cookie handling, or null if not needed
	 * @param expectedStatusCode The expected status code of the response, or -1 if not needed
	 * @param withAuth Whether to include authentication headers in the request
	 * @return A RequestResponse object containing the response details
	 * @throws IOException if there is an error executing the request or processing the response
	 */
	public static RequestResponse executeContextJSONRequest(HttpUriRequest request, String sessionId, int expectedStatusCode, boolean withAuth) throws IOException {
		try (CloseableHttpResponse response = HttpClientThatWaitsForUnomi.doRequest(request, expectedStatusCode, withAuth, false)) {
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

	private static <T extends Item> boolean removeAllItems(DefinitionsService definitionsService, PersistenceService persistenceService,
			boolean allTenants, TenantService tenantService, ExecutionContextManager executionContextManager,
			String conditionType, String itemType, Class<T> clazz) {
		Condition condition = new Condition(definitionsService.getConditionType(conditionType));
		condition.setParameter("propertyName", "itemType");
		condition.setParameter("comparisonOperator", "equals");
		condition.setParameter("propertyValue", itemType);

		if (allTenants) {
			List<Tenant> tenants = tenantService.getAllTenants();
			boolean success = true;
			// First remove from system tenant
			Boolean systemResult = executionContextManager.executeAsTenant(TenantService.SYSTEM_TENANT, () -> 
				persistenceService.removeByQuery(condition, clazz));
			success &= systemResult;
			// Then remove from all other tenants
			for (Tenant tenant : tenants) {
				Boolean tenantResult = executionContextManager.executeAsTenant(tenant.getItemId(), () -> 
					persistenceService.removeByQuery(condition, clazz));
				success &= tenantResult;
			}
			return success;
		} else {
			return persistenceService.removeByQuery(condition, clazz);
		}
	}

	private static <T extends Item> void verifyItemsRemoved(DefinitionsService definitionsService, PersistenceService persistenceService,
			boolean allTenants, TenantService tenantService, ExecutionContextManager executionContextManager,
			String itemType) {
		if (allTenants) {
			List<Tenant> tenants = tenantService.getAllTenants();
			// Check all tenants in parallel with a single keepTrying loop
			keepTrying(itemType + " not removed from all tenants", () -> {
				// Check system tenant
				Condition countCondition = new Condition(definitionsService.getConditionType("matchAllCondition"));
				Long systemCount = executionContextManager.executeAsTenant(TenantService.SYSTEM_TENANT, () ->
					persistenceService.queryCount(countCondition, itemType));
				
				if (systemCount > 0L) {
					return false;
				}

				// Check each tenant
				for (Tenant tenant : tenants) {
					final String tenantId = tenant.getItemId();
					Long tenantCount = executionContextManager.executeAsTenant(tenantId, () ->
						persistenceService.queryCount(countCondition, itemType));
					if (tenantCount > 0L) {
						return false;
					}
				}
				return true;
			}, (Boolean success) -> success, DEFAULT_TRYING_TIMEOUT * 2, DEFAULT_TRYING_TRIES * 2);
		} else {
			// Check current tenant only
			keepTrying(itemType + " not removed from current tenant", () -> {
				Condition countCondition = new Condition(definitionsService.getConditionType("matchAllCondition"));
				return persistenceService.queryCount(countCondition, itemType);
			}, (Long count) -> count == 0L, DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);
		}
	}

	private static <T> void keepTrying(String message, Supplier<T> supplier, Predicate<T> predicate, int timeout, int maxTries) {
		int tries = 0;
		T result = null;
		while (tries < maxTries) {
			result = supplier.get();
			if (predicate.test(result)) {
				return;
			}
			try {
				Thread.sleep(timeout / maxTries);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new RuntimeException("Interrupted while waiting for condition", e);
			}
			tries++;
		}
		throw new RuntimeException(message + " after " + maxTries + " tries: last result was " + result.toString());
	}

	/**
	 * Removes all profiles from the persistence service.
	 * Creates and executes a query to delete all items of type 'profile'.
	 * If allTenants is true, it will remove profiles from all tenants including the system tenant.
	 * If allTenants is false, it will only remove profiles from the current tenant.
	 * After removal, it verifies that all profiles have been successfully removed.
	 *
	 * @param definitionsService The service providing condition type definitions
	 * @param persistenceService The service handling data persistence
	 * @param allTenants Whether to remove profiles from all tenants (true) or just the current tenant (false)
	 * @param tenantService The service to get all tenants
	 * @param executionContextManager The manager to handle tenant context execution
	 * @return true if the removal was successful, false otherwise
	 */
	public static boolean removeAllProfiles(DefinitionsService definitionsService, PersistenceService persistenceService, 
			boolean allTenants, TenantService tenantService, ExecutionContextManager executionContextManager) {
		boolean success = removeAllItems(definitionsService, persistenceService, allTenants, tenantService, 
			executionContextManager, "profilePropertyCondition", "profile", Profile.class);
		verifyItemsRemoved(definitionsService, persistenceService, allTenants, tenantService, 
			executionContextManager, "profile");
		return success;
	}

	/**
	 * Removes all events from the persistence service.
	 * Creates and executes a query to delete all items of type 'event'.
	 * If allTenants is true, it will remove events from all tenants including the system tenant.
	 * If allTenants is false, it will only remove events from the current tenant.
	 * After removal, it verifies that all events have been successfully removed.
	 *
	 * @param definitionsService The service providing condition type definitions
	 * @param persistenceService The service handling data persistence
	 * @param allTenants Whether to remove events from all tenants (true) or just the current tenant (false)
	 * @param tenantService The service to get all tenants
	 * @param executionContextManager The manager to handle tenant context execution
	 * @return true if the removal was successful, false otherwise
	 */
	public static boolean removeAllEvents(DefinitionsService definitionsService, PersistenceService persistenceService, 
			boolean allTenants, TenantService tenantService, ExecutionContextManager executionContextManager) {
		boolean success = removeAllItems(definitionsService, persistenceService, allTenants, tenantService, 
			executionContextManager, "eventPropertyCondition", "event", Event.class);
		verifyItemsRemoved(definitionsService, persistenceService, allTenants, tenantService, 
			executionContextManager, "event");
		return success;
	}

	/**
	 * Removes all sessions from the persistence service.
	 * Creates and executes a query to delete all items of type 'session'.
	 * If allTenants is true, it will remove sessions from all tenants including the system tenant.
	 * If allTenants is false, it will only remove sessions from the current tenant.
	 * After removal, it verifies that all sessions have been successfully removed.
	 *
	 * @param definitionsService The service providing condition type definitions
	 * @param persistenceService The service handling data persistence
	 * @param allTenants Whether to remove sessions from all tenants (true) or just the current tenant (false)
	 * @param tenantService The service to get all tenants
	 * @param executionContextManager The manager to handle tenant context execution
	 * @return true if the removal was successful, false otherwise
	 */
	public static boolean removeAllSessions(DefinitionsService definitionsService, PersistenceService persistenceService, 
			boolean allTenants, TenantService tenantService, ExecutionContextManager executionContextManager) {
		boolean success = removeAllItems(definitionsService, persistenceService, allTenants, tenantService, 
			executionContextManager, "sessionPropertyCondition", "session", Session.class);
		verifyItemsRemoved(definitionsService, persistenceService, allTenants, tenantService, 
			executionContextManager, "session");
		return success;
	}

	/**
	 * Removes all profiles from the persistence service for the current tenant only.
	 * This is a convenience method that calls removeAllProfiles with allTenants set to false.
	 *
	 * @param definitionsService The service providing condition type definitions
	 * @param persistenceService The service handling data persistence
	 * @return true if the removal was successful, false otherwise
	 */
	public static boolean removeAllProfiles(DefinitionsService definitionsService, PersistenceService persistenceService) {
		return removeAllProfiles(definitionsService, persistenceService, false, null, null);
	}

	/**
	 * Removes all events from the persistence service for the current tenant only.
	 * This is a convenience method that calls removeAllEvents with allTenants set to false.
	 *
	 * @param definitionsService The service providing condition type definitions
	 * @param persistenceService The service handling data persistence
	 * @return true if the removal was successful, false otherwise
	 */
	public static boolean removeAllEvents(DefinitionsService definitionsService, PersistenceService persistenceService) {
		return removeAllEvents(definitionsService, persistenceService, false, null, null);
	}

	/**
	 * Removes all sessions from the persistence service for the current tenant only.
	 * This is a convenience method that calls removeAllSessions with allTenants set to false.
	 *
	 * @param definitionsService The service providing condition type definitions
	 * @param persistenceService The service handling data persistence
	 * @return true if the removal was successful, false otherwise
	 */
	public static boolean removeAllSessions(DefinitionsService definitionsService, PersistenceService persistenceService) {
		return removeAllSessions(definitionsService, persistenceService, false, null, null);
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

	/**
	 * Inner class representing the response from a context service request.
	 * Contains the HTTP status code, cookie header value, and deserialized context response.
	 */
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
