package org.oasis_open.wemi.context.server.api.services;

import org.oasis_open.wemi.context.server.api.User;

import java.util.List;

/**
 * Created by loom on 24.04.14.
 */
public interface UserService {

    List<User> findUsersByPropertyValue(String propertyName, String propertyValue);

    User load(String userId);

    boolean save(User user);

    public List<String> getUserProperties();

}
