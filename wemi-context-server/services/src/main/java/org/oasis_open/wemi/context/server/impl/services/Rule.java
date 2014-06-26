package org.oasis_open.wemi.context.server.impl.services;

import org.oasis_open.wemi.context.server.api.consequences.Consequence;
import org.oasis_open.wemi.context.server.impl.consequences.SetPropertyConsequence;

import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonValue;
import java.util.HashSet;
import java.util.Set;

/**
* Created by toto on 26/06/14.
*/
class Rule {
    private JsonObject source;

    Rule(JsonObject source) {
        this.source = source;
    }

    public Set<Consequence> getConsequences() {
        // todo build consequences list based on consequence definitions

        Set<Consequence> result = new HashSet<Consequence>();
        JsonArray array = source.getJsonArray("consequences");
        for (JsonValue jsonValue : array) {
            final JsonObject parameters = ((JsonObject) jsonValue).getJsonObject("parameters");
            SetPropertyConsequence cons = new SetPropertyConsequence();
            cons.setPropertyName(parameters.getString("propertyName"));
            cons.setPropertyValue(parameters.getString("propertyValue"));
            result.add(cons);
        }
        return result;
    }
}
