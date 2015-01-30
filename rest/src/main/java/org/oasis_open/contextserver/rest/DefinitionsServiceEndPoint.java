package org.oasis_open.contextserver.rest;

import org.apache.cxf.helpers.IOUtils;
import org.apache.cxf.rs.security.cors.CrossOriginResourceSharing;
import org.oasis_open.contextserver.api.*;
import org.oasis_open.contextserver.api.actions.ActionType;
import org.oasis_open.contextserver.api.conditions.ConditionType;
import org.oasis_open.contextserver.api.conditions.initializers.ChoiceListInitializer;
import org.oasis_open.contextserver.api.conditions.initializers.ChoiceListValue;
import org.oasis_open.contextserver.api.conditions.initializers.I18nSupport;
import org.oasis_open.contextserver.api.services.DefinitionsService;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;

import javax.jws.WebMethod;
import javax.jws.WebService;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@WebService
@Produces(MediaType.APPLICATION_JSON + ";charset=UTF-8")
@CrossOriginResourceSharing(
        allowAllOrigins = true,
        allowCredentials = true
)
public class DefinitionsServiceEndPoint {

    private static final Pattern I18N_PATTERN = Pattern.compile("#\\{([a-zA-Z_\\.]+)\\}");

    private DefinitionsService definitionsService;
    private BundleContext bundleContext;
    private ResourceBundleHelper resourceBundleHelper;

    @WebMethod(exclude = true)
    public void setDefinitionsService(DefinitionsService definitionsService) {
        this.definitionsService = definitionsService;
    }

    @WebMethod(exclude = true)
    public void setBundleContext(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    @WebMethod(exclude = true)
    public void setResourceBundleHelper(ResourceBundleHelper resourceBundleHelper) {
        this.resourceBundleHelper = resourceBundleHelper;
    }

    @GET
    @Path("/tags")
    public Collection<RESTTag> getAllTags(@HeaderParam("Accept-Language") String language) {
        return generateTags(definitionsService.getAllTags(), language);
    }

    @GET
    @Path("/rootTags")
    public Collection<RESTTag> getRootTags(@HeaderParam("Accept-Language") String language) {
        return generateTags(definitionsService.getRootTags(), language);
    }

    @GET
    @Path("/tags/{tagId}")
    public RESTTag getTag(@PathParam("tagId") String tag, @HeaderParam("Accept-Language") String language) {
        return generateTag(definitionsService.getTag(tag), language);
    }

    @GET
    @Path("/conditions")
    public Collection<RESTConditionType> getAllConditionTypes(@HeaderParam("Accept-Language") String language) {
        Collection<ConditionType> conditionTypes = definitionsService.getAllConditionTypes();
        return generateConditions(conditionTypes, null, language);
    }


    @GET
    @Path("/conditions/tags/{tagId}")
    public Collection<RESTConditionType> getConditionTypesByTag(@PathParam("tagId") String tags, @QueryParam("recursive") @DefaultValue("false") boolean recursive, @HeaderParam("Accept-Language") String language) {
        String[] tagsArray = tags.split(",");
        HashSet<ConditionType> results = new HashSet<ConditionType>();
        for (String s : tagsArray) {
            results.addAll(definitionsService.getConditionTypesByTag(definitionsService.getTag(s), recursive));
        }
        return generateConditions(results, null, language);
    }

    @GET
    @Path("/conditions/{conditionId}")
    public RESTConditionType getConditionType(@PathParam("conditionId") String id, @HeaderParam("Accept-Language") String language) {
        ConditionType conditionType = definitionsService.getConditionType(id);
        return generateCondition(conditionType, null, language);
    }

    @GET
    @Path("/actions")
    public Collection<RESTActionType> getAllActionTypes(@HeaderParam("Accept-Language") String language) {
        Collection<ActionType> actionTypes = definitionsService.getAllActionTypes();
        return generateActions(actionTypes, null, language);
    }

    @GET
    @Path("/actions/tags/{tagId}")
    public Collection<RESTActionType> getActionTypeByTag(@PathParam("tagId") String tags, @QueryParam("recursive") @DefaultValue("false") boolean recursive, @HeaderParam("Accept-Language") String language) {
        String[] tagsArray = tags.split(",");
        HashSet<ActionType> results = new HashSet<ActionType>();
        for (String s : tagsArray) {
            results.addAll(definitionsService.getActionTypeByTag(definitionsService.getTag(s), recursive));
        }
        return generateActions(results, null, language);
    }

    @GET
    @Path("/actions/{actionId}")
    public RESTActionType getActionType(@PathParam("actionId") String id, @HeaderParam("Accept-Language") String language) {
        ActionType actionType = definitionsService.getActionType(id);
        return generateAction(actionType, null, language);
    }

    @GET
    @Path("/values")
    public Collection<RESTValueType> getAllValueTypes(@HeaderParam("Accept-Language") String language) {
        return generateValueTypes(definitionsService.getAllValueTypes(), language);
    }

    @GET
    @Path("/values/tags/{tagId}")
    public Collection<RESTValueType> getValueTypeByTag(@PathParam("tagId") String tags, @QueryParam("recursive") @DefaultValue("false") boolean recursive, @HeaderParam("Accept-Language") String language) {
        String[] tagsArray = tags.split(",");
        HashSet<ValueType> results = new HashSet<ValueType>();
        for (String s : tagsArray) {
            results.addAll(definitionsService.getValueTypeByTag(definitionsService.getTag(s), recursive));
        }
        return generateValueTypes(results, language);
    }

    @GET
    @Path("/values/{valueTypeId}")
    public RESTValueType getValueType(@PathParam("valueTypeId") String id, @HeaderParam("Accept-Language") String language) {
        ValueType valueType = definitionsService.getValueType(id);
        return generateValueType(valueType, language);
    }

    @GET
    @Path("/properties/{target}")
    public Collection<RESTPropertyType> getAllPropertyTypes(@PathParam("target") String target, @HeaderParam("Accept-Language") String language) {
        return generatePropertyTypes(definitionsService.getAllPropertyTypes(target), language);
    }

    @GET
    @Path("/properties/tags/{tagId}")
    public Collection<RESTPropertyType> getPropertyTypeByTag(@PathParam("tagId") String tags, @QueryParam("recursive") @DefaultValue("false") boolean recursive, @HeaderParam("Accept-Language") String language) {
        String[] tagsArray = tags.split(",");
        HashSet<PropertyType> results = new HashSet<PropertyType>();
        for (String s : tagsArray) {
            results.addAll(definitionsService.getPropertyTypeByTag(definitionsService.getTag(s), recursive));
        }
        return generatePropertyTypes(results, language);
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

    private Collection<RESTConditionType> generateConditions(Collection<ConditionType> conditionTypes, Object context, String language) {
        List<RESTConditionType> result = new ArrayList<RESTConditionType>();
        if (conditionTypes == null) {
            return result;
        }
        for (ConditionType conditionType : conditionTypes) {
            result.add(generateCondition(conditionType, context, language));
        }
        return result;
    }

    private Collection<RESTActionType> generateActions(Collection<ActionType> actionTypes, Object context, String language) {
        List<RESTActionType> result = new ArrayList<RESTActionType>();
        if (actionTypes == null) {
            return result;
        }
        for (ActionType actionType : actionTypes) {
            result.add(generateAction(actionType, context, language));
        }
        return result;
    }

    private RESTConditionType generateCondition(ConditionType conditionType, Object context, String language) {
        RESTConditionType result = new RESTConditionType();
        result.setId(conditionType.getId());

        ResourceBundle bundle = resourceBundleHelper.getResourceBundle(conditionType, language);
        result.setName(resourceBundleHelper.getResourceBundleValue(bundle, conditionType.getNameKey()));
        result.setDescription(resourceBundleHelper.getResourceBundleValue(bundle, conditionType.getDescriptionKey()));

        result.setTags(conditionType.getTagIDs());

        for (Parameter parameter : conditionType.getParameters()) {
            result.getParameters().add(generateParameter(parameter, context, bundle));
        }

        return result;
    }

    private RESTActionType generateAction(ActionType actionType, Object context, String language) {
        RESTActionType result = new RESTActionType();
        result.setId(actionType.getId());

        ResourceBundle bundle = resourceBundleHelper.getResourceBundle(actionType, language);
        result.setName(resourceBundleHelper.getResourceBundleValue(bundle, actionType.getNameKey()));
        result.setDescription(resourceBundleHelper.getResourceBundleValue(bundle, actionType.getDescriptionKey()));

        result.setTags(actionType.getTagIds());

        List<RESTParameter> parameters = new ArrayList<RESTParameter>();
        for (Parameter parameter : actionType.getParameters()) {
            parameters.add(generateParameter(parameter, context, bundle));
        }
        result.setParameters(parameters);

        return result;
    }

    private RESTParameter generateParameter(Parameter parameter, Object context, ResourceBundle bundle) {
        RESTParameter result = new RESTParameter();
        result.setId(parameter.getId());
        result.setDefaultValue(parameter.getDefaultValue());
        result.setMultivalued(parameter.isMultivalued());
        result.setType(parameter.getType());
        
        localizeChoiceListValues(bundle, result.getChoiceListValues(), parameter.getChoiceListInitializerFilter());

        return result;
    }

    private Collection<RESTPropertyType> generatePropertyTypes(Collection<PropertyType> type, String language) {
        Set<RESTPropertyType> result = new LinkedHashSet<>();
        for (PropertyType propertyType : type) {
            result.add(generatePropertyType(propertyType, resourceBundleHelper.getResourceBundle(propertyType, language)));
        }
        return result;
    }

    private RESTPropertyType generatePropertyType(PropertyType type, ResourceBundle bundle) {
        RESTPropertyType result = new RESTPropertyType();
        result.setId(type.getId());
        result.setName(resourceBundleHelper.getResourceBundleValue(bundle, type.getNameKey()));
        result.setType(type.getValueTypeId());
        result.setDefaultValue(type.getDefaultValue());
        result.setRank(type.getRank());
        result.setTags(type.getTagIds());
        result.setAutomaticMappingsFrom(type.getAutomaticMappingsFrom());
        result.setMergeStrategy(type.getMergeStrategy());
        result.setSelectorId(type.getSelectorId());

        localizeChoiceListValues(bundle, result.getChoiceListValues(), type.getChoiceListInitializerFilter());

        return result;
    }

    private void localizeChoiceListValues(ResourceBundle bundle, List<ChoiceListValue> result, String choiceListInitializerFilter) {
        if (choiceListInitializerFilter != null && choiceListInitializerFilter.length() > 0) {
            try {
                Collection<ServiceReference<ChoiceListInitializer>> matchingChoiceListInitializerReferences = bundleContext.getServiceReferences(ChoiceListInitializer.class, choiceListInitializerFilter);
                for (ServiceReference<ChoiceListInitializer> choiceListInitializerReference : matchingChoiceListInitializerReferences) {
                    ChoiceListInitializer choiceListInitializer = bundleContext.getService(choiceListInitializerReference);
                    List<ChoiceListValue> options = choiceListInitializer.getValues(bundle.getLocale());
                    if (choiceListInitializer instanceof I18nSupport) {
                        for (ChoiceListValue value : options) {
                            result.add(value.localizedCopy(resourceBundleHelper.getResourceBundleValue(bundle, value.getName())));
                        }
                    } else {
                        result.addAll(options);
                    }
                }
            } catch (InvalidSyntaxException e) {
                e.printStackTrace();
            }
        }
    }

    private Collection<RESTValueType> generateValueTypes(Collection<ValueType> valueTypes, String language) {
        List<RESTValueType> result = new ArrayList<RESTValueType>();
        if (valueTypes == null) {
            return result;
        }
        for (ValueType valueType : valueTypes) {
            result.add(generateValueType(valueType, language));
        }
        return result;
    }

    private RESTValueType generateValueType(ValueType valueType, String language) {
        RESTValueType result = new RESTValueType();
        result.setId(valueType.getId());

        ResourceBundle bundle = resourceBundleHelper.getResourceBundle(valueType, language);
        result.setName(resourceBundleHelper.getResourceBundleValue(bundle, valueType.getNameKey()));
        result.setDescription(resourceBundleHelper.getResourceBundleValue(bundle, valueType.getDescriptionKey()));
        result.setTags(generateTags(valueType.getTags(), language));
        return result;
    }

    private Collection<RESTTag> generateTags(Collection<Tag> tags, String language) {
        List<RESTTag> result = new ArrayList<RESTTag>();
        for (Tag tag : tags) {
            result.add(generateTag(tag, language));
        }
        return result;
    }

    private RESTTag generateTag(Tag tag, String language) {
        RESTTag result = new RESTTag();
        result.setId(tag.getId());
        ResourceBundle bundle = resourceBundleHelper.getResourceBundle(tag, language);
        result.setName(resourceBundleHelper.getResourceBundleValue(bundle, tag.getNameKey()));
        result.setDescription(resourceBundleHelper.getResourceBundleValue(bundle, tag.getDescriptionKey()));
        result.setParentId(tag.getParentId());
        result.setRank(tag.getRank());
        result.setSubTags(generateTags(tag.getSubTags(), language));
        return result;
    }
}
