package org.oasis_open.contextserver.privacy;

import javax.xml.bind.annotation.XmlRootElement;

/**
 * Created by loom on 10.09.15.
 */
@XmlRootElement
public class EventInfo {

    private String name;
    private String description;
    private Long occurences;

    public EventInfo() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Long getOccurences() {
        return occurences;
    }

    public void setOccurences(Long occurences) {
        this.occurences = occurences;
    }
}
