package org.oasis_open.contextserver.rest;

import org.apache.cxf.rs.security.cors.CrossOriginResourceSharing;
import org.oasis_open.contextserver.api.*;
import org.oasis_open.contextserver.api.*;
import org.oasis_open.contextserver.api.actions.ActionType;
import org.oasis_open.contextserver.api.conditions.ConditionType;
import org.oasis_open.contextserver.api.conditions.initializers.ChoiceListInitializer;
import org.oasis_open.contextserver.api.services.DefinitionsService;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;

import javax.jws.WebMethod;
import javax.jws.WebService;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.*;

@WebService
@Produces(MediaType.APPLICATION_JSON)
@CrossOriginResourceSharing(
        allowAllOrigins = true,
        allowCredentials = true
)
public class DefinitionsServiceEndPoint {

    DefinitionsService definitionsService;
    BundleContext bundleContext;

    @WebMethod(exclude = true)
    public void setDefinitionsService(DefinitionsService definitionsService) {
        this.definitionsService = definitionsService;
    }

    @WebMethod(exclude = true)
    public void setBundleContext(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    @GET
    @Path("/tags")
    public Set<Tag> getAllTags() {
        return definitionsService.getAllTags();
    }

    @GET
    @Path("/rootTags")
    public Set<Tag> getRootTags() {
        return definitionsService.getRootTags();
    }

    @GET
    @Path("/tags/{tagId}")
    public Tag getTag(@PathParam("tagId") String tag) {
        return definitionsService.getTag(tag);
    }

    @GET
    @Path("/conditions")
    public Collection<ConditionType> getAllConditionTypes() {
        Collection<ConditionType> conditionTypes = definitionsService.getAllConditionTypes();
        generateConditionChoiceListValues(conditionTypes, null);
        return conditionTypes;
    }


    @GET
    @Path("/conditions/tags/{tagId}")
    public Set<ConditionType> getConditionTypesByTag(@PathParam("tagId") String tags, @QueryParam("recursive") @DefaultValue("false") boolean recursive) {
        String[] tagsArray = tags.split(",");
        HashSet<ConditionType> results = new HashSet<ConditionType>();
        for (String s : tagsArray) {
            results.addAll(definitionsService.getConditionTypesByTag(definitionsService.getTag(s), recursive));
        }
        generateConditionChoiceListValues(results, null);
        return results;
    }

    @GET
    @Path("/conditions/{conditionId}")
    public ConditionType getConditionType(@PathParam("conditionId") String id) {
        ConditionType conditionType = definitionsService.getConditionType(id);
        generateChoiceListValues(conditionType, null);
        return conditionType;
    }

    @GET
    @Path("/actions")
    public Collection<ActionType> getAllActionTypes() {
        Collection<ActionType> actionTypes = definitionsService.getAllActionTypes();
        generateActionChoiceListValues(actionTypes, null);
        return actionTypes;
    }

    @GET
    @Path("/actions/tags/{tagId}")
    public Set<ActionType> getActionTypeByTag(@PathParam("tagId") String tags, @QueryParam("recursive") @DefaultValue("false") boolean recursive) {
        String[] tagsArray = tags.split(",");
        HashSet<ActionType> results = new HashSet<ActionType>();
        for (String s : tagsArray) {
            results.addAll(definitionsService.getActionTypeByTag(definitionsService.getTag(s), recursive));
        }
        generateActionChoiceListValues(results, null);
        return results;
    }

    @GET
    @Path("/actions/{actionId}")
    public ActionType getActionType(@PathParam("actionId") String id) {
        ActionType actionType = definitionsService.getActionType(id);
        generateChoiceListValues(actionType, null);
        return actionType;
    }

    @GET
    @Path("/values")
    public Collection<ValueType> getAllValueTypes() {
        return definitionsService.getAllValueTypes();
    }

    @GET
    @Path("/values/tags/{tagId}")
    public Set<ValueType> getValueTypeByTag(@PathParam("tagId") String tags, @QueryParam("recursive") @DefaultValue("false") boolean recursive) {
        String[] tagsArray = tags.split(",");
        HashSet<ValueType> results = new HashSet<ValueType>();
        for (String s : tagsArray) {
            results.addAll(definitionsService.getValueTypeByTag(definitionsService.getTag(s), recursive));
        }
        return results;
    }

    @GET
    @Path("/values/{valueTypeId}")
    public ValueType getValueType(@PathParam("valueTypeId") String id) {
        return definitionsService.getValueType(id);
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

    private void generateConditionChoiceListValues(Collection<ConditionType> conditionTypes, Object context) {
        if (conditionTypes == null) {
            return;
        }
        for (ConditionType conditionType : conditionTypes) {
            generateChoiceListValues(conditionType, null);
        }
    }

    private void generateActionChoiceListValues(Collection<ActionType> actionTypes, Object context) {
        if (actionTypes == null) {
            return;
        }
        for (ActionType actionType : actionTypes) {
            generateChoiceListValues(actionType, null);
        }
    }

    private void generateChoiceListValues(ConditionType conditionType, Object context) {
        for (Parameter parameter : conditionType.getParameters()) {
            generateChoiceListValues(parameter, context);
        }
    }

    private void generateChoiceListValues(ActionType actionType, Object context) {
        for (Parameter parameter : actionType.getParameters()) {
            generateChoiceListValues(parameter, context);
        }
    }

    private void generateChoiceListValues(Parameter parameter, Object context) {
        if (parameter.getChoicelistInitializerFilter() != null && parameter.getChoicelistInitializerFilter().length() > 0) {
            try {
                Collection<ServiceReference<ChoiceListInitializer>> matchingChoiceListInitializerReferences = bundleContext.getServiceReferences(ChoiceListInitializer.class, parameter.getChoicelistInitializerFilter());
                for (ServiceReference<ChoiceListInitializer> choiceListInitializerReference : matchingChoiceListInitializerReferences) {
                    ChoiceListInitializer choiceListInitializer = bundleContext.getService(choiceListInitializerReference);
                    parameter.setChoiceListValues(choiceListInitializer.getValues(context));
                }
            } catch (InvalidSyntaxException e) {
                e.printStackTrace();
            }
        }
    }

}
