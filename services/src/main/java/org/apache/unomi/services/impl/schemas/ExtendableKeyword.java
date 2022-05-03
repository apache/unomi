package org.apache.unomi.services.impl.schemas;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.*;
import org.apache.unomi.api.schema.json.JSONSchema;
import java.text.MessageFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class ExtendableKeyword extends AbstractKeyword {

    SchemaServiceImpl schemaService;

    public class ExtendableJsonValidator extends AbstractJsonValidator {

        String schemaPath;
        JsonNode schemaNode;
        JsonSchema parentSchema;
        ValidationContext validationContext;
        SchemaServiceImpl schemaService;

        protected ExtendableJsonValidator(String keyword, String schemaPath, JsonNode schemaNode, JsonSchema parentSchema,
                                          ValidationContext validationContext, SchemaServiceImpl schemaService) {
            super(keyword);
            this.schemaPath = schemaPath;
            this.schemaNode = schemaNode;
            this.parentSchema = parentSchema;
            this.validationContext = validationContext;
            this.schemaService = schemaService;
        }

        @Override
        public Set<ValidationMessage> validate(JsonNode node, JsonNode rootNode, String at) {
            String schemaId = rootNode.get("$id").asText();
            List<JSONSchema> schemaExtensions = schemaService.getSchemaExtensions(schemaId);
            List<JSONSchema> andExtensions = schemaExtensions.stream().filter(schema -> schema.getExtensionOperator().equals("and")).collect(Collectors.toList());
            List<JSONSchema> orExtensions = schemaExtensions.stream().filter(schema -> schema.getExtensionOperator().equals("or")).collect(Collectors.toList());

            Set<ValidationMessage> validationMessages = new LinkedHashSet<>();
            if (!andExtensions.stream().allMatch(extensionSchema -> {
                Set<ValidationMessage> andValidationMessages = schemaService.getJsonSchema(extensionSchema.getSchemaId()).validate(node);
                validationMessages.addAll(andValidationMessages);
                return andValidationMessages.size() == 0;
            })) {
                validationMessages.add(buildValidationMessage(CustomErrorMessageType
                                .of("and-schema-not-valid", new MessageFormat("{0} : invalid values for extension of id={1}")), at,
                        schemaId));
            }
            if (!orExtensions.stream().anyMatch(extensionSchema -> {
                Set<ValidationMessage> anyValidationMessages = schemaService.getJsonSchema(extensionSchema.getSchemaId()).validate(node);
                validationMessages.addAll(anyValidationMessages);
                return anyValidationMessages.size() == 0;
            })) {
                validationMessages.add(buildValidationMessage(CustomErrorMessageType
                                .of("or-schema-not-valid", new MessageFormat("{0} : invalid values for extension of id={1}")), at,
                        schemaId));
            }
            return validationMessages;
        }
    }

    public ExtendableKeyword(SchemaServiceImpl schemaService) {
        super("extendable");
        this.schemaService = schemaService;
    }

    @Override
    public JsonValidator newValidator(String schemaPath, JsonNode jsonNode, JsonSchema jsonSchema, ValidationContext validationContext) throws JsonSchemaException, Exception {
        return new ExtendableJsonValidator(this.getValue(), schemaPath, jsonNode, jsonSchema, validationContext,
                schemaService);
    }
}
