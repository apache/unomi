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
import org.oasis_open.contextserver.api.*;
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

    @GET
    @Path("/tags")
    public Collection<RESTTag> getAllTags(@HeaderParam("Accept-Language") String language) {
        return localizationHelper.generateTags(definitionsService.getAllTags(), language);
    }

    @GET
    @Path("/rootTags")
    public Collection<RESTTag> getRootTags(@HeaderParam("Accept-Language") String language) {
        return localizationHelper.generateTags(definitionsService.getRootTags(), language);
    }

    @GET
    @Path("/tags/{tagId}")
    public RESTTag getTag(@PathParam("tagId") String tag, @HeaderParam("Accept-Language") String language) {
        return localizationHelper.generateTag(definitionsService.getTag(tag), language);
    }

    @GET
    @Path("/conditions")
    public Collection<RESTConditionType> getAllConditionTypes(@HeaderParam("Accept-Language") String language) {
        Collection<ConditionType> conditionTypes = definitionsService.getAllConditionTypes();
        return localizationHelper.generateConditions(conditionTypes, null, language);
    }


    @GET
    @Path("/conditions/tags/{tagId}")
    public Collection<RESTConditionType> getConditionTypesByTag(@PathParam("tagId") String tags, @QueryParam("recursive") @DefaultValue("false") boolean recursive, @HeaderParam("Accept-Language") String language) {
        String[] tagsArray = tags.split(",");
        HashSet<ConditionType> results = new HashSet<ConditionType>();
        for (String s : tagsArray) {
            results.addAll(definitionsService.getConditionTypesByTag(definitionsService.getTag(s), recursive));
        }
        return localizationHelper.generateConditions(results, null, language);
    }

    @GET
    @Path("/conditions/{conditionId}")
    public RESTConditionType getConditionType(@PathParam("conditionId") String id, @HeaderParam("Accept-Language") String language) {
        ConditionType conditionType = definitionsService.getConditionType(id);
        return localizationHelper.generateCondition(conditionType, null, language);
    }

    @GET
    @Path("/actions")
    public Collection<RESTActionType> getAllActionTypes(@HeaderParam("Accept-Language") String language) {
        Collection<ActionType> actionTypes = definitionsService.getAllActionTypes();
        return localizationHelper.generateActions(actionTypes, null, language);
    }

    @GET
    @Path("/actions/tags/{tagId}")
    public Collection<RESTActionType> getActionTypeByTag(@PathParam("tagId") String tags, @QueryParam("recursive") @DefaultValue("false") boolean recursive, @HeaderParam("Accept-Language") String language) {
        String[] tagsArray = tags.split(",");
        HashSet<ActionType> results = new HashSet<ActionType>();
        for (String s : tagsArray) {
            results.addAll(definitionsService.getActionTypeByTag(definitionsService.getTag(s), recursive));
        }
        return localizationHelper.generateActions(results, null, language);
    }

    @GET
    @Path("/actions/{actionId}")
    public RESTActionType getActionType(@PathParam("actionId") String id, @HeaderParam("Accept-Language") String language) {
        ActionType actionType = definitionsService.getActionType(id);
        return localizationHelper.generateAction(actionType, null, language);
    }

    @GET
    @Path("/values")
    public Collection<RESTValueType> getAllValueTypes(@HeaderParam("Accept-Language") String language) {
        return localizationHelper.generateValueTypes(definitionsService.getAllValueTypes(), language);
    }

    @GET
    @Path("/values/tags/{tagId}")
    public Collection<RESTValueType> getValueTypeByTag(@PathParam("tagId") String tags, @QueryParam("recursive") @DefaultValue("false") boolean recursive, @HeaderParam("Accept-Language") String language) {
        String[] tagsArray = tags.split(",");
        HashSet<ValueType> results = new HashSet<ValueType>();
        for (String s : tagsArray) {
            results.addAll(definitionsService.getValueTypeByTag(definitionsService.getTag(s), recursive));
        }
        return localizationHelper.generateValueTypes(results, language);
    }

    @GET
    @Path("/values/{valueTypeId}")
    public RESTValueType getValueType(@PathParam("valueTypeId") String id, @HeaderParam("Accept-Language") String language) {
        ValueType valueType = definitionsService.getValueType(id);
        return localizationHelper.generateValueType(valueType, language);
    }

    @GET
    @Path("/properties")
    public Map<String, Collection<RESTPropertyType>> getPropertyTypes(@HeaderParam("Accept-Language") String language) {
        Map<String, Collection<PropertyType>> propertyTypes = definitionsService.getAllPropertyTypes();
        Map<String, Collection<RESTPropertyType>> result = new HashMap<>();

        for (String id : propertyTypes.keySet()){
            result.put(id, localizationHelper.generatePropertyTypes(propertyTypes.get(id), language));
        }

        return result;
    }

    @GET
    @Path("/properties/{target}")
    public Collection<RESTPropertyType> getPropertyTypesByTarget(@PathParam("target") String target, @HeaderParam("Accept-Language") String language) {
        return localizationHelper.generatePropertyTypes(definitionsService.getAllPropertyTypes(target), language);
    }

    @GET
    @Path("/properties/tags/{tagId}")
    public Collection<RESTPropertyType> getPropertyTypeByTag(@PathParam("tagId") String tags, @QueryParam("recursive") @DefaultValue("false") boolean recursive, @HeaderParam("Accept-Language") String language) {
        String[] tagsArray = tags.split(",");
        HashSet<PropertyType> results = new HashSet<PropertyType>();
        for (String s : tagsArray) {
            results.addAll(definitionsService.getPropertyTypeByTag(definitionsService.getTag(s), recursive));
        }
        return localizationHelper.generatePropertyTypes(results, language);
    }

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
