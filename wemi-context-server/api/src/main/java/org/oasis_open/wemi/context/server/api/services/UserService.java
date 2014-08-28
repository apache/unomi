package org.oasis_open.wemi.context.server.api.services;

import org.oasis_open.wemi.context.server.api.Session;
import org.oasis_open.wemi.context.server.api.User;
import org.oasis_open.wemi.context.server.api.UserProperty;
import org.oasis_open.wemi.context.server.api.UserPropertyGroup;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Created by loom on 24.04.14.
 */
public interface UserService {

    Collection<User> getAllUsers();

    Collection<User> getUsers(String query, int offset, int size);

    List<User> findUsersByPropertyValue(String propertyName, String propertyValue);

    User load(String userId);

    boolean save(User user);

    public Set<UserPropertyGroup> getUserPropertyGroups();

    public Set<UserProperty> getAllUserProperties();

    public Set<UserProperty> getUserProperties(String propertyGroupId);

    public String getUserPropertyMapping(String fromPropertyName);

    Session loadSession(String eventId);

    boolean saveSession(Session event);

    boolean matchCondition(String condition, User user, Session session);
}
