package org.oasis_open.wemi.context.server.api.conditions.initializers;

/**
 * Created by loom on 26.06.14.
 */
public class ChoiceListValue {

    private String id;
    private String name;

    public ChoiceListValue(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
