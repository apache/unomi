package org.oasis_open.contextserver.privacy;

import javax.xml.bind.annotation.XmlRootElement;
import java.util.List;
import java.util.Map;

/**
 * Created by loom on 10.09.15.
 */
@XmlRootElement
public class ServerInfo {

    private String serverIdentifier;
    private String serverVersion;

    private List<EventInfo> eventTypes;
    private Map<String,String> capabilities;

    public ServerInfo() {
    }

    public String getServerIdentifier() {
        return serverIdentifier;
    }

    public void setServerIdentifier(String serverIdentifier) {
        this.serverIdentifier = serverIdentifier;
    }

    public String getServerVersion() {
        return serverVersion;
    }

    public void setServerVersion(String serverVersion) {
        this.serverVersion = serverVersion;
    }

    public List<EventInfo> getEventTypes() {
        return eventTypes;
    }

    public void setEventTypes(List<EventInfo> eventTypes) {
        this.eventTypes = eventTypes;
    }

    public Map<String, String> getCapabilities() {
        return capabilities;
    }

    public void setCapabilities(Map<String, String> capabilities) {
        this.capabilities = capabilities;
    }
}
