package org.oasis_open.contextserver.api;

import javax.xml.bind.annotation.XmlRootElement;

/**
 * Created by loom on 10.09.15.
 */
@XmlRootElement
public class EventInfo {

    private String name;
    private Long occurences;

    public EventInfo() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getOccurences() {
        return occurences;
    }

    public void setOccurences(Long occurences) {
        this.occurences = occurences;
    }
}
