package org.oasis_open.wemi.context.server.impl.services;

import org.oasis_open.wemi.context.server.api.User;
import org.oasis_open.wemi.context.server.api.services.UserService;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by loom on 24.04.14.
 */
public class UserServiceImpl implements UserService {

    public List<User> findUsersByPropertyValue(String propertyName, String propertyValue) {
        return new ArrayList<User>();
    }

    public boolean save(User user) {
        return false;
    }
}
