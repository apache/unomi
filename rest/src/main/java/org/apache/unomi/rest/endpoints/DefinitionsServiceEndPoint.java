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

package org.apache.unomi.rest.endpoints;

import org.apache.cxf.rs.security.cors.CrossOriginResourceSharing;
import org.apache.unomi.api.PluginType;
import org.apache.unomi.api.PropertyMergeStrategyType;
import org.apache.unomi.api.ValueType;
import org.apache.unomi.api.actions.ActionType;
import org.apache.unomi.api.conditions.ConditionType;
import org.apache.unomi.api.services.DefinitionsService;
import org.apache.unomi.rest.models.RESTActionType;
import org.apache.unomi.rest.models.RESTConditionType;
import org.apache.unomi.rest.models.RESTValueType;
import org.apache.unomi.rest.service.impl.LocalizationHelper;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.*;

/**
 * JAX-RS endpoint for condition, action, and value type definitions used by the rules engine.
 */
@Produces(MediaType.APPLICATION_JSON + ";charset=UTF-8")
@CrossOriginResourceSharing(
        allowAllOrigins = true,
        allowCredentials = true
)
@Path("/definitions")
@Component(service=DefinitionsServiceEndPoint.class,property = "osgi.jaxrs.resource=true")
public class DefinitionsServiceEndPoint {
    @Reference
    private DefinitionsService definitionsService;

    @Reference
    private LocalizationHelper localizationHelper;

    /**
     * Sets the definitions service.
     *
     * @param definitionsService the definitions service
     */
    public void setDefinitionsService(DefinitionsService definitionsService) {
        this.definitionsService = definitionsService;
    }

    /**
     * Sets the localization helper.
     *
     * @param localizationHelper the localization helper
     */
    public void setLocalizationHelper(LocalizationHelper localizationHelper) {
        this.localizationHelper = localizationHelper;
    }

    /**
     * Returns all condition types localized for the requested language.
     * <p>
     * Labels and descriptions follow the {@code Accept-Language} header when provided.
     *
     * @param language the locale to use for labels and descriptions (from {@code Accept-Language})
     * @return all condition types in REST form
     * @api.status 200 array org.apache.unomi.rest.models.RESTConditionType All condition types (may be empty).
     * @api.example [{"id":"profilePropertyCondition","name":"Profile property","description":"Checks a profile property value","tags":["profile"],"systemTags":["profile"],"parameters":[{"id":"propertyName","type":"string"},{"id":"comparisonOperator","type":"string"},{"id":"propertyValue","type":"string"}],"version":1}]
     */
    @GET
    @Path("/conditions")
    public Collection<RESTConditionType> getAllConditionTypes(@HeaderParam("Accept-Language") String language) {
        Collection<ConditionType> conditionTypes = definitionsService.getAllConditionTypes();
        return localizationHelper.generateConditions(conditionTypes, language);
    }

    /**
     * Returns condition types that match any of the given tags.
     * <p>
     * The {@code tags} path segment is a comma-separated list; each tag is matched independently
     * and results are de-duplicated.
     *
     * @param tags a comma-separated list of tag identifiers
     * @param language the locale to use for labels and descriptions (from {@code Accept-Language})
     * @return matching condition types in REST form
     * @api.status 200 array org.apache.unomi.rest.models.RESTConditionType Matching condition types (may be empty).
     * @api.example [{"id":"eventTypeCondition","name":"Event type","description":"Matches events of a given type","tags":["event"],"systemTags":["event"],"parameters":[{"id":"eventTypeId","type":"string"}],"version":1}]
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
     * Returns condition types that match any of the given system tags.
     * <p>
     * The {@code tags} path segment is a comma-separated list; each system tag is matched
     * independently and results are de-duplicated.
     *
     * @param tags a comma-separated list of system tag identifiers
     * @param language the locale to use for labels and descriptions (from {@code Accept-Language})
     * @return matching condition types in REST form
     * @api.status 200 array org.apache.unomi.rest.models.RESTConditionType Matching condition types (may be empty).
     * @api.example [{"id":"profilePropertyCondition","name":"Profile property","description":"Checks a profile property value","tags":["profile"],"systemTags":["profile"],"parameters":[{"id":"propertyName","type":"string"},{"id":"comparisonOperator","type":"string"},{"id":"propertyValue","type":"string"}],"version":1}]
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
     * Returns the condition type with the given ID, localized for the requested language.
     * When the condition type does not exist the endpoint returns {@code null} (HTTP 200 with empty body).
     *
     * @param id the condition type identifier
     * @param language the locale to use for labels and descriptions (from {@code Accept-Language})
     * @return the condition type in REST form, or {@code null} when it does not exist
     * @api.status 200 org.apache.unomi.rest.models.RESTConditionType Condition type found, or empty body when missing.
     * @api.example {"id":"profilePropertyCondition","name":"Profile property","description":"Checks a profile property value","tags":["profile"],"systemTags":["profile"],"parameters":[{"id":"propertyName","type":"string"},{"id":"comparisonOperator","type":"string"},{"id":"propertyValue","type":"string"}],"version":1}
     */
    @GET
    @Path("/conditions/{conditionId}")
    public RESTConditionType getConditionType(@PathParam("conditionId") String id, @HeaderParam("Accept-Language") String language) {
        ConditionType conditionType = definitionsService.getConditionType(id);
        return conditionType != null ? localizationHelper.generateCondition(conditionType, language) : null;
    }

    /**
     * Stores the given condition type definition.
     * Body is a full {@link ConditionType}: {@code metadata}, evaluator metadata, and {@code parameters}.
     *
     * @param conditionType the condition type to store
     * @api.status 204 empty Condition type created or updated.
     * @api.status 400 empty Invalid or unreadable condition type body.
     * @api.example {"itemId":"profilePropertyCondition","itemType":"conditionType","metadata":{"id":"profilePropertyCondition","name":"Profile property","scope":"systemscope","enabled":true},"conditionEvaluator":"profilePropertyConditionEvaluator","parameters":[{"id":"propertyName","type":"string"},{"id":"comparisonOperator","type":"string"},{"id":"propertyValue","type":"string"}]}
     */
    @POST
    @Path("/conditions")
    public void setConditionType(ConditionType conditionType) {
        definitionsService.setConditionType(conditionType);
    }

    /**
     * Deletes the condition type with the given ID.
     *
     * @param conditionTypeId the condition type identifier
     * @api.status 204 empty Condition type deleted.
     * @api.example {"itemId":"profilePropertyCondition","itemType":"conditionType","metadata":{"id":"profilePropertyCondition","name":"Profile property","scope":"systemscope","enabled":true},"conditionEvaluator":"profilePropertyConditionEvaluator","parameters":[{"id":"propertyName","type":"string"},{"id":"comparisonOperator","type":"string"},{"id":"propertyValue","type":"string"}]}
     */
    @DELETE
    @Path("/conditions/{conditionTypeId}")
    public void removeConditionType(@PathParam("conditionTypeId") String conditionTypeId) {
        definitionsService.removeConditionType(conditionTypeId);
    }

    /**
     * Returns all action types localized for the requested language.
     * <p>
     * Labels and descriptions follow the {@code Accept-Language} header when provided.
     *
     * @param language the locale to use for labels and descriptions (from {@code Accept-Language})
     * @return all action types in REST form
     * @api.status 200 array org.apache.unomi.rest.models.RESTActionType All action types (may be empty).
     * @api.example [{"id":"setPropertyAction","name":"Set property","description":"Sets a profile or session property","tags":["profile"],"systemTags":["profile"],"parameters":[{"id":"setPropertyName","type":"string"},{"id":"setPropertyValueBoolean","type":"boolean"}],"version":1}]
     */
    @GET
    @Path("/actions")
    public Collection<RESTActionType> getAllActionTypes(@HeaderParam("Accept-Language") String language) {
        Collection<ActionType> actionTypes = definitionsService.getAllActionTypes();
        return localizationHelper.generateActions(actionTypes, language);
    }

    /**
     * Returns action types that match any of the given tags.
     * <p>
     * The {@code tags} path segment is a comma-separated list; each tag is matched independently
     * and results are de-duplicated.
     *
     * @param tags a comma-separated list of tag identifiers
     * @param language the locale to use for labels and descriptions (from {@code Accept-Language})
     * @return matching action types in REST form
     * @api.status 200 array org.apache.unomi.rest.models.RESTActionType Matching action types (may be empty).
     * @api.example [{"id":"setPropertyAction","name":"Set property","description":"Sets a profile or session property","tags":["profile"],"systemTags":["profile"],"parameters":[{"id":"setPropertyName","type":"string"},{"id":"setPropertyValueBoolean","type":"boolean"}],"version":1}]
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
     * Returns action types that match any of the given system tags.
     * <p>
     * The {@code tags} path segment is a comma-separated list; each system tag is matched
     * independently and results are de-duplicated.
     *
     * @param tags a comma-separated list of system tag identifiers
     * @param language the locale to use for labels and descriptions (from {@code Accept-Language})
     * @return matching action types in REST form
     * @api.status 200 array org.apache.unomi.rest.models.RESTActionType Matching action types (may be empty).
     * @api.example [{"id":"setPropertyAction","name":"Set property","description":"Sets a profile or session property","tags":["profile"],"systemTags":["profile"],"parameters":[{"id":"setPropertyName","type":"string"},{"id":"setPropertyValueBoolean","type":"boolean"}],"version":1}]
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
     * Returns the action type with the given ID, localized for the requested language.
     * When the action type does not exist the endpoint returns {@code null} (HTTP 200 with empty body).
     *
     * @param id the action type identifier
     * @param language the locale to use for labels and descriptions (from {@code Accept-Language})
     * @return the action type in REST form, or {@code null} when it does not exist
     * @api.status 200 org.apache.unomi.rest.models.RESTActionType Action type found, or empty body when missing.
     * @api.example {"id":"setPropertyAction","name":"Set property","description":"Sets a profile or session property","tags":["profile"],"systemTags":["profile"],"parameters":[{"id":"setPropertyName","type":"string"},{"id":"setPropertyValueBoolean","type":"boolean"}],"version":1}
     */
    @GET
    @Path("/actions/{actionId}")
    public RESTActionType getActionType(@PathParam("actionId") String id, @HeaderParam("Accept-Language") String language) {
        ActionType actionType = definitionsService.getActionType(id);
        return actionType != null ? localizationHelper.generateAction(actionType, language) : null;
    }

    /**
     * Stores the given action type definition.
     * Body is a full {@link ActionType}: {@code metadata}, executor metadata, and {@code parameters}.
     *
     * @param actionType the action type to store
     * @api.status 204 empty Action type created or updated.
     * @api.status 400 empty Invalid or unreadable action type body.
     * @api.example {"itemId":"setPropertyAction","itemType":"actionType","metadata":{"id":"setPropertyAction","name":"Set property","scope":"systemscope","enabled":true},"actionExecutor":"setPropertyActionExecutor","parameters":[{"id":"setPropertyName","type":"string"},{"id":"setPropertyValueBoolean","type":"boolean"}]}
     */
    @POST
    @Path("/actions")
    public void setActionType(ActionType actionType) {
        definitionsService.setActionType(actionType);
    }

    /**
     * Deletes the action type with the given ID.
     *
     * @param actionTypeId the action type identifier
     * @api.status 204 empty Action type deleted.
     * @api.example {"itemId":"setPropertyAction","itemType":"actionType","metadata":{"id":"setPropertyAction","name":"Set property","scope":"systemscope","enabled":true},"actionExecutor":"setPropertyActionExecutor","parameters":[{"id":"setPropertyName","type":"string"},{"id":"setPropertyValueBoolean","type":"boolean"}]}
     */
    @DELETE
    @Path("/actions/{actionTypeId}")
    public void removeActionType(@PathParam("actionTypeId") String actionTypeId) {
        definitionsService.removeActionType(actionTypeId);
    }

    /**
     * Returns all value types localized for the requested language.
     * <p>
     * Labels and descriptions follow the {@code Accept-Language} header when provided.
     *
     * @param language the locale to use for labels and descriptions (from {@code Accept-Language})
     * @return all value types in REST form
     * @api.status 200 array org.apache.unomi.rest.models.RESTValueType All value types (may be empty).
     * @api.example [{"id":"string","name":"String","description":"Text value","tags":["primitive"]}]
     */
    @GET
    @Path("/values")
    public Collection<RESTValueType> getAllValueTypes(@HeaderParam("Accept-Language") String language) {
        return localizationHelper.generateValueTypes(definitionsService.getAllValueTypes(), language);
    }

    /**
     * Returns value types that match any of the given tags.
     * <p>
     * The {@code tags} path segment is a comma-separated list; each tag is matched independently
     * and results are de-duplicated.
     *
     * @param tags a comma-separated list of tag identifiers
     * @param language the locale to use for labels and descriptions (from {@code Accept-Language})
     * @return matching value types in REST form
     * @api.status 200 array org.apache.unomi.rest.models.RESTValueType Matching value types (may be empty).
     * @api.example [{"id":"string","name":"String","description":"Text value","tags":["primitive"]}]
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
     * Returns the value type with the given ID, localized for the requested language.
     *
     * @param id the value type identifier
     * @param language the locale to use for labels and descriptions (from {@code Accept-Language})
     * @return the value type in REST form
     * @api.status 200 org.apache.unomi.rest.models.RESTValueType Value type found.
     * @api.status 404 empty Value type not found.
     * @api.example {"id":"string","name":"String","description":"Text value","tags":["primitive"]}
     */
    @GET
    @Path("/values/{valueTypeId}")
    public RESTValueType getValueType(@PathParam("valueTypeId") String id, @HeaderParam("Accept-Language") String language) {
        ValueType valueType = definitionsService.getValueType(id);
        if (valueType == null) {
            throw new NotFoundException("Value type not found: " + id);
        }
        return localizationHelper.generateValueType(valueType, language);
    }

    /**
     * Returns plugin types grouped by plugin identifier.
     * <p>
     * Keys are numeric plugin IDs; values are lists of condition and action types contributed by each plugin.
     *
     * @return plugin ID to plugin type list mappings
     * @api.status 200 empty Map of plugin id to condition/action type lists (may be empty).
     * @api.example {"42":[{"itemId":"profilePropertyCondition","itemType":"conditionType"}]}
     */
    @GET
    @Path("/typesByPlugin")
    public Map<Long, List<PluginType>> getTypesByPlugin() {
        return definitionsService.getTypesByPlugin();
    }

    /**
     * Returns the property merge strategy type for the given identifier.
     *
     * @param id the property merge strategy type identifier
     * @return the property merge strategy type
     */
    public PropertyMergeStrategyType getPropertyMergeStrategyType(String id) {
        return definitionsService.getPropertyMergeStrategyType(id);
    }

}
