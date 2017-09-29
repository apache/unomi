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

package org.apache.unomi.rest;

import org.apache.cxf.rs.security.cors.CrossOriginResourceSharing;
import org.apache.unomi.api.PluginType;
import org.apache.unomi.api.PropertyMergeStrategyType;
import org.apache.unomi.api.ValueType;
import org.apache.unomi.api.actions.ActionType;
import org.apache.unomi.api.conditions.ConditionType;
import org.apache.unomi.api.services.DefinitionsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jws.WebMethod;
import javax.jws.WebService;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.*;

/**
 * A JAX-RS endpoint to retrieve definition information about core context server entities such as conditions, actions and values.
 */
@WebService
@Produces(MediaType.APPLICATION_JSON + ";charset=UTF-8")
@CrossOriginResourceSharing(
        allowAllOrigins = true,
        allowCredentials = true
)
public class DefinitionsServiceEndPoint {
    private static final Logger logger = LoggerFactory.getLogger(DefinitionsServiceEndPoint.class.getName());

    private DefinitionsService definitionsService;
    private LocalizationHelper localizationHelper;

    @WebMethod(exclude = true)
    public void setDefinitionsService(DefinitionsService definitionsService) {
        this.definitionsService = definitionsService;
    }

    @WebMethod(exclude = true)
    public void setLocalizationHelper(LocalizationHelper localizationHelper) {
        this.localizationHelper = localizationHelper;
    }

    /**
     * Retrieves all condition types localized using the specified language.
     *
     * @param language the language to use to localize.
     * @return a Collection of all collection types
     */
    @GET
    @Path("/conditions")
    public Collection<RESTConditionType> getAllConditionTypes(@HeaderParam("Accept-Language") String language) {
        Collection<ConditionType> conditionTypes = definitionsService.getAllConditionTypes();
        return localizationHelper.generateConditions(conditionTypes, language);
    }

    /**
     * Retrieves the set of condition types with the specified tags.
     *
     * @param language  the language to use to localize.
     * @param tags      a comma-separated list of tag identifiers
     * @return the set of condition types with the specified tag
     */
    @GET
    @Path("/conditions/tags/{tags}")
    public Collection<RESTConditionType> getConditionTypesByTag(@PathParam("tags") String tags, @HeaderParam("Accept-Language") String language) {
        String[] tagsArray = tags.split(",");
        Set<ConditionType> results = new LinkedHashSet<>();
        for (String tag : tagsArray) {
            results.addAll(definitionsService.getConditionTypesByTag(tag));
        }
        return localizationHelper.generateConditions(results, language);
    }

    /**
     * Retrieves the set of condition types with the specified system tags.
     *
     * @param language  the language to use to localize.
     * @param tags      a comma-separated list of tag identifiers
     * @return the set of condition types with the specified tag
     */
    @GET
    @Path("/conditions/systemTags/{tags}")
    public Collection<RESTConditionType> getConditionTypesBySystemTag(@PathParam("tags") String tags, @HeaderParam("Accept-Language") String language) {
        String[] tagsArray = tags.split(",");
        Set<ConditionType> results = new LinkedHashSet<>();
        for (String tag : tagsArray) {
            results.addAll(definitionsService.getConditionTypesBySystemTag(tag));
        }
        return localizationHelper.generateConditions(results, language);
    }

    /**
     * Retrieves the condition type associated with the specified identifier localized using the specified language.
     *
     * @param language the language to use to localize.
     * @param id       the identifier of the condition type to retrieve
     * @return the condition type associated with the specified identifier or {@code null} if no such condition type exists
     */
    @GET
    @Path("/conditions/{conditionId}")
    public RESTConditionType getConditionType(@PathParam("conditionId") String id, @HeaderParam("Accept-Language") String language) {
        ConditionType conditionType = definitionsService.getConditionType(id);
        return conditionType != null ? localizationHelper.generateCondition(conditionType, language) : null;
    }

    /**
     * Stores the condition type
     *
     * @param conditionType the condition type to store
     */
    @POST
    @Path("/conditions")
    public void setConditionType(ConditionType conditionType) {
        definitionsService.setConditionType(conditionType);
    }

    /**
     * Removes the condition type
     *
     * @param conditionTypeId the identifier of the action type to delete
     */
    @DELETE
    @Path("/conditions/{conditionTypeId}")
    public void removeConditionType(@PathParam("conditionTypeId") String conditionTypeId) {
        definitionsService.removeConditionType(conditionTypeId);
    }

    /**
     * Retrieves all known action types localized using the specified language.
     *
     * @param language the language to use to localize.
     * @return all known action types
     */
    @GET
    @Path("/actions")
    public Collection<RESTActionType> getAllActionTypes(@HeaderParam("Accept-Language") String language) {
        Collection<ActionType> actionTypes = definitionsService.getAllActionTypes();
        return localizationHelper.generateActions(actionTypes, language);
    }

    /**
     * Retrieves the set of action types with the specified tags.
     *
     * @param language  the language to use to localize.
     * @param tags      the tag marking the action types we want to retrieve
     * @return the set of action types with the specified tag
     */
    @GET
    @Path("/actions/tags/{tags}")
    public Collection<RESTActionType> getActionTypeByTag(@PathParam("tags") String tags, @HeaderParam("Accept-Language") String language) {
        String[] tagsArray = tags.split(",");
        Set<ActionType> results = new LinkedHashSet<>();
        for (String tag : tagsArray) {
            results.addAll(definitionsService.getActionTypeByTag(tag));
        }
        return localizationHelper.generateActions(results, language);
    }

    /**
     * Retrieves the set of action types with the specified system tags.
     *
     * @param language  the language to use to localize.
     * @param tags      the tag marking the action types we want to retrieve
     * @return the set of action types with the specified tag
     */
    @GET
    @Path("/actions/systemTags/{tags}")
    public Collection<RESTActionType> getActionTypeBySystemTag(@PathParam("tags") String tags, @HeaderParam("Accept-Language") String language) {
        String[] tagsArray = tags.split(",");
        Set<ActionType> results = new LinkedHashSet<>();
        for (String tag : tagsArray) {
            results.addAll(definitionsService.getActionTypeBySystemTag(tag));
        }
        return localizationHelper.generateActions(results, language);
    }

    /**
     * Retrieves the action type associated with the specified identifier localized using the specified language.
     *
     * @param language the language to use to localize.
     * @param id       the identifier of the action type to retrieve
     * @return the action type associated with the specified identifier or {@code null} if no such action type exists
     */
    @GET
    @Path("/actions/{actionId}")
    public RESTActionType getActionType(@PathParam("actionId") String id, @HeaderParam("Accept-Language") String language) {
        ActionType actionType = definitionsService.getActionType(id);
        return actionType != null ? localizationHelper.generateAction(actionType, language) : null;
    }

    /**
     * Stores the action type
     *
     * @param actionType the action type to store
     */
    @POST
    @Path("/actions")
    public void setActionType(ActionType actionType) {
        definitionsService.setActionType(actionType);
    }

    /**
     * Removes the action type
     *
     * @param actionTypeId the identifier of the action type to delete
     */
    @DELETE
    @Path("/actions/{actionTypeId}")
    public void removeActionType(@PathParam("actionTypeId") String actionTypeId) {
        definitionsService.removeActionType(actionTypeId);
    }

    /**
     * Retrieves all known value types localized using the specified language.
     *
     * @param language the language to use to localize.
     * @return all known value types
     */
    @GET
    @Path("/values")
    public Collection<RESTValueType> getAllValueTypes(@HeaderParam("Accept-Language") String language) {
        return localizationHelper.generateValueTypes(definitionsService.getAllValueTypes(), language);
    }

    /**
     * Retrieves the set of value types with the specified tags.
     *
     * @param language  the language to use to localize.
     * @param tags      the tag marking the value types we want to retrieve
     * @return the set of value types with the specified tag
     */
    @GET
    @Path("/values/tags/{tags}")
    public Collection<RESTValueType> getValueTypeByTag(@PathParam("tags") String tags, @HeaderParam("Accept-Language") String language) {
        String[] tagsArray = tags.split(",");
        Set<ValueType> results = new LinkedHashSet<>();
        for (String tag : tagsArray) {
            results.addAll(definitionsService.getValueTypeByTag(tag));
        }
        return localizationHelper.generateValueTypes(results, language);
    }

    /**
     * Retrieves the value type associated with the specified identifier localized using the specified language.
     *
     * @param language the language to use to localize.
     * @param id       the identifier of the value type to retrieve
     * @return the value type associated with the specified identifier or {@code null} if no such value type exists
     */
    @GET
    @Path("/values/{valueTypeId}")
    public RESTValueType getValueType(@PathParam("valueTypeId") String id, @HeaderParam("Accept-Language") String language) {
        ValueType valueType = definitionsService.getValueType(id);
        return localizationHelper.generateValueType(valueType, language);
    }

    /**
     * Retrieves a Map of plugin identifier to a list of plugin types defined by that particular plugin.
     *
     * @return a Map of plugin identifier to a list of plugin types defined by that particular plugin
     */
    @GET
    @Path("/typesByPlugin")
    public Map<Long, List<PluginType>> getTypesByPlugin() {
        return definitionsService.getTypesByPlugin();
    }

    @WebMethod(exclude = true)
    public PropertyMergeStrategyType getPropertyMergeStrategyType(String id) {
        return definitionsService.getPropertyMergeStrategyType(id);
    }

}
