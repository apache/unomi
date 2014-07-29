package org.oasis_open.wemi.context.server.rest;

import org.apache.cxf.rs.security.cors.CrossOriginResourceSharing;
import org.oasis_open.wemi.context.server.api.conditions.Parameter;
import org.oasis_open.wemi.context.server.api.conditions.Tag;
import org.oasis_open.wemi.context.server.api.conditions.ConditionType;
import org.oasis_open.wemi.context.server.api.conditions.initializers.ChoiceListInitializer;
import org.oasis_open.wemi.context.server.api.consequences.ConsequenceType;
import org.oasis_open.wemi.context.server.api.services.DefinitionsService;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;

import javax.jws.WebMethod;
import javax.jws.WebService;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.Collection;
import java.util.Set;

@WebService
@Produces(MediaType.APPLICATION_JSON)
@CrossOriginResourceSharing(
        allowAllOrigins = true,
        allowCredentials = true
)
public class DefinitionsServiceEndPoint implements DefinitionsService {

    DefinitionsService definitionsService;
    BundleContext bundleContext;

    @WebMethod(exclude=true)
    public void setDefinitionsService(DefinitionsService definitionsService) {
        this.definitionsService = definitionsService;
    }

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
    public Set<Tag> getChildTags(@PathParam("tagId") Tag tag) {
        return definitionsService.getChildTags(tag);
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
    public Set<ConditionType> getConditionTypesByTag(@PathParam("tagId") Tag tag) {
        Set<ConditionType> conditionTypes = definitionsService.getConditionTypesByTag(tag);
        generateConditionChoiceListValues(conditionTypes, null);
        return conditionTypes;
    }

    @GET
    @Path("/conditions/{conditionId}")
    public ConditionType getConditionType(@PathParam("conditionId") String conditionId) {
        ConditionType conditionType = definitionsService.getConditionType(conditionId);
        generateChoiceListValues(conditionType, null);
        return conditionType;
    }

    @GET
    @Path("/consequences")
    public Collection<ConsequenceType> getAllConsequenceTypes() {
        Collection<ConsequenceType> consequenceTypes = definitionsService.getAllConsequenceTypes();
        generateConsequenceChoiceListValues(consequenceTypes, null);
        return consequenceTypes;
    }

    @GET
    @Path("/consequences/tags/{tagId}")
    public Set<ConsequenceType> getConsequenceTypeByTag(@PathParam("tagId") Tag tag) {
        Set<ConsequenceType> consequenceTypes = definitionsService.getConsequenceTypeByTag(tag);
        generateConsequenceChoiceListValues(consequenceTypes, null);
        return consequenceTypes;
    }

    @GET
    @Path("/consequences/{consequenceId}")
    public ConsequenceType getConsequenceType(@PathParam("consequenceId") String consequenceId) {
        ConsequenceType consequenceType = definitionsService.getConsequenceType(consequenceId);
        generateChoiceListValues(consequenceType, null);
        return consequenceType;
    }

    private void generateConditionChoiceListValues(Collection<ConditionType> conditionTypes, Object context) {
        for (ConditionType conditionType : conditionTypes) {
            generateChoiceListValues(conditionType, null);
        }
    }

    private void generateConsequenceChoiceListValues(Collection<ConsequenceType> consequenceTypes, Object context) {
        for (ConsequenceType consequenceType : consequenceTypes) {
            generateChoiceListValues(consequenceType, null);
        }
    }

    private void generateChoiceListValues(ConditionType conditionType, Object context) {
        for (Parameter parameter : conditionType.getParameters()) {
            generateChoiceListValues(parameter, context);
        }
    }

    private void generateChoiceListValues(ConsequenceType consequenceType, Object context) {
        for (Parameter parameter : consequenceType.getParameters()) {
            generateChoiceListValues(parameter, context);
        }
    }

    private void generateChoiceListValues(Parameter parameter, Object context) {
        if (parameter.getChoiceListInitializerFilter() != null && parameter.getChoiceListInitializerFilter().length() > 0) {
            try {
                Collection<ServiceReference<ChoiceListInitializer>> matchingChoiceListInitializerReferences = bundleContext.getServiceReferences(ChoiceListInitializer.class, parameter.getChoiceListInitializerFilter());
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
