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
     *
     * @param language the locale to use for labels and descriptions
     * @return all condition types in REST form
     */
    @GET
    @Path("/conditions")
    public Collection<RESTConditionType> getAllConditionTypes(@HeaderParam("Accept-Language") String language) {
        Collection<ConditionType> conditionTypes = definitionsService.getAllConditionTypes();
        return localizationHelper.generateConditions(conditionTypes, language);
    }

    /**
     * Returns condition types that match any of the given tags.
     *
     * @param language the locale to use for labels and descriptions
     * @param tags a comma-separated list of tag identifiers
     * @return matching condition types in REST form
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
     *
     * @param language the locale to use for labels and descriptions
     * @param tags a comma-separated list of system tag identifiers
     * @return matching condition types in REST form
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
     *
     * @param language the locale to use for labels and descriptions
     * @param id the condition type identifier
     * @return the condition type in REST form, or {@code null} when it does not exist
     */
    @GET
    @Path("/conditions/{conditionId}")
    public RESTConditionType getConditionType(@PathParam("conditionId") String id, @HeaderParam("Accept-Language") String language) {
        ConditionType conditionType = definitionsService.getConditionType(id);
        return conditionType != null ? localizationHelper.generateCondition(conditionType, language) : null;
    }

    /**
     * Stores the given condition type definition.
     *
     * @param conditionType the condition type to store
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
     */
    @DELETE
    @Path("/conditions/{conditionTypeId}")
    public void removeConditionType(@PathParam("conditionTypeId") String conditionTypeId) {
        definitionsService.removeConditionType(conditionTypeId);
    }

    /**
     * Returns all action types localized for the requested language.
     *
     * @param language the locale to use for labels and descriptions
     * @return all action types in REST form
     */
    @GET
    @Path("/actions")
    public Collection<RESTActionType> getAllActionTypes(@HeaderParam("Accept-Language") String language) {
        Collection<ActionType> actionTypes = definitionsService.getAllActionTypes();
        return localizationHelper.generateActions(actionTypes, language);
    }

    /**
     * Returns action types that match any of the given tags.
     *
     * @param language the locale to use for labels and descriptions
     * @param tags a comma-separated list of tag identifiers
     * @return matching action types in REST form
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
     *
     * @param language the locale to use for labels and descriptions
     * @param tags a comma-separated list of system tag identifiers
     * @return matching action types in REST form
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
     *
     * @param language the locale to use for labels and descriptions
     * @param id the action type identifier
     * @return the action type in REST form, or {@code null} when it does not exist
     */
    @GET
    @Path("/actions/{actionId}")
    public RESTActionType getActionType(@PathParam("actionId") String id, @HeaderParam("Accept-Language") String language) {
        ActionType actionType = definitionsService.getActionType(id);
        return actionType != null ? localizationHelper.generateAction(actionType, language) : null;
    }

    /**
     * Stores the given action type definition.
     *
     * @param actionType the action type to store
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
     */
    @DELETE
    @Path("/actions/{actionTypeId}")
    public void removeActionType(@PathParam("actionTypeId") String actionTypeId) {
        definitionsService.removeActionType(actionTypeId);
    }

    /**
     * Returns all value types localized for the requested language.
     *
     * @param language the locale to use for labels and descriptions
     * @return all value types in REST form
     */
    @GET
    @Path("/values")
    public Collection<RESTValueType> getAllValueTypes(@HeaderParam("Accept-Language") String language) {
        return localizationHelper.generateValueTypes(definitionsService.getAllValueTypes(), language);
    }

    /**
     * Returns value types that match any of the given tags.
     *
     * @param language the locale to use for labels and descriptions
     * @param tags a comma-separated list of tag identifiers
     * @return matching value types in REST form
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
     * @param language the locale to use for labels and descriptions
     * @param id the value type identifier
     * @return the value type in REST form, or {@code null} when it does not exist
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
     *
     * @return plugin ID to plugin type list mappings
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
