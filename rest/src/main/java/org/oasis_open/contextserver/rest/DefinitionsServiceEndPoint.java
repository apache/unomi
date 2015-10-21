package org.oasis_open.contextserver.rest;

/*
 * #%L
 * context-server-rest
 * $Id:$
 * $HeadURL:$
 * %%
 * Copyright (C) 2014 - 2015 Jahia Solutions
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import org.apache.cxf.rs.security.cors.CrossOriginResourceSharing;
import org.oasis_open.contextserver.api.PluginType;
import org.oasis_open.contextserver.api.PropertyMergeStrategyType;
import org.oasis_open.contextserver.api.ValueType;
import org.oasis_open.contextserver.api.actions.ActionType;
import org.oasis_open.contextserver.api.conditions.ConditionType;
import org.oasis_open.contextserver.api.services.DefinitionsService;
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
     * Retrieves all known tags localized using the specified language.
     *
     * @param language the language to use to localize
     * @return the set of all known tags
     */
    @GET
    @Path("/tags")
    public Collection<RESTTag> getAllTags(@HeaderParam("Accept-Language") String language) {
        return localizationHelper.generateTags(definitionsService.getAllTags(), language);
    }

    /**
     * Retrieves the set of all root tags from which all other tags are derived via sub-tags localized using the specified language.
     *
     * @param language the language to use to localize.
     * @return the set of all root tags
     */
    @GET
    @Path("/rootTags")
    public Collection<RESTTag> getRootTags(@HeaderParam("Accept-Language") String language) {
        return localizationHelper.generateTags(definitionsService.getRootTags(), language);
    }

    /**
     * Retrieves the tag with the specified identifier localized using the specified language.
     *
     * @param language     the language to use to localize.
     * @param tag          the identifier of the tag to retrieve
     * @param filterHidden {@code true} if hidden sub-tags should be filtered out, {@code false} otherwise
     * @return the tag with the specified identifier
     */
    @GET
    @Path("/tags/{tagId}")
    public RESTTag getTag(@PathParam("tagId") String tag, @QueryParam("filterHidden") @DefaultValue("false") boolean filterHidden, @HeaderParam("Accept-Language") String language) {
        return localizationHelper.generateTag(definitionsService.getTag(tag), language, filterHidden);
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
     * Retrieves the set of condition types with the specified tags also retrieving condition types from sub-tags if so specified localized using the specified language.
     *
     * @param language  the language to use to localize.
     * @param tags      a comma-separated list of tag identifiers
     * @param recursive {@code true} if we want to also include condition types marked by sub-tags of the specified tag
     * @return the set of condition types with the specified tag (and its sub-tags, if specified)
     */
    @GET
    @Path("/conditions/tags/{tagId}")
    public Collection<RESTConditionType> getConditionTypesByTag(@PathParam("tagId") String tags, @QueryParam("recursive") @DefaultValue("false") boolean recursive, @HeaderParam("Accept-Language") String language) {
        String[] tagsArray = tags.split(",");
        Set<ConditionType> results = new LinkedHashSet<>();
        for (String s : tagsArray) {
            results.addAll(definitionsService.getConditionTypesByTag(definitionsService.getTag(s), recursive));
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
        return localizationHelper.generateCondition(conditionType, language);
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
     * Retrieves the set of action types with the specified tags also retrieving action types from sub-tags if so specified localized using the specified language.
     *
     * @param language  the language to use to localize.
     * @param tags      the tag marking the action types we want to retrieve
     * @param recursive {@code true} if we want to also include action types marked by sub-tags of the specified tag
     * @return the set of action types with the specified tag (and its sub-tags, if specified)
     */
    @GET
    @Path("/actions/tags/{tagId}")
    public Collection<RESTActionType> getActionTypeByTag(@PathParam("tagId") String tags, @QueryParam("recursive") @DefaultValue("false") boolean recursive, @HeaderParam("Accept-Language") String language) {
        String[] tagsArray = tags.split(",");
        Set<ActionType> results = new LinkedHashSet<>();
        for (String s : tagsArray) {
            results.addAll(definitionsService.getActionTypeByTag(definitionsService.getTag(s), recursive));
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
        return localizationHelper.generateAction(actionType, language);
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
     * Retrieves the set of value types with the specified tags also retrieving value types from sub-tags if so specified localized using the specified language.
     *
     * @param language  the language to use to localize.
     * @param tags      the tag marking the value types we want to retrieve
     * @param recursive {@code true} if we want to also include value types marked by sub-tags of the specified tag
     * @return the set of value types with the specified tag (and its sub-tags, if specified)
     */
    @GET
    @Path("/values/tags/{tagId}")
    public Collection<RESTValueType> getValueTypeByTag(@PathParam("tagId") String tags, @QueryParam("recursive") @DefaultValue("false") boolean recursive, @HeaderParam("Accept-Language") String language) {
        String[] tagsArray = tags.split(",");
        Set<ValueType> results = new LinkedHashSet<>();
        for (String s : tagsArray) {
            results.addAll(definitionsService.getValueTypeByTag(definitionsService.getTag(s), recursive));
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
