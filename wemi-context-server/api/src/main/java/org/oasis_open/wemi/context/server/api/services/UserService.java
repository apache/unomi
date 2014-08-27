package org.oasis_open.wemi.context.server.api.services;

import org.oasis_open.wemi.context.server.api.Metadata;
import org.oasis_open.wemi.context.server.api.Session;
import org.oasis_open.wemi.context.server.api.User;

import java.util.List;
import java.util.Set;

/**
 * Created by loom on 24.04.14.
 */
public interface UserService {

    Set<Metadata> getUserMetadatas();

    List<User> findUsersByPropertyValue(String propertyName, String propertyValue);

    User load(String userId);

    boolean save(User user);

    public List<String> getUserProperties();

    Session loadSession(String eventId);

    boolean saveSession(Session event);

    boolean matchCondition(String condition, User user, Session session);
}
