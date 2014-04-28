package org.oasis_open.wemi.context.server.impl.services;

import org.oasis_open.wemi.context.server.api.User;
import org.oasis_open.wemi.context.server.api.services.UserService;
import org.ops4j.pax.cdi.api.OsgiServiceProvider;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Default;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by loom on 24.04.14.
 */
@ApplicationScoped
@Default
@OsgiServiceProvider
public class UserServiceImpl implements UserService {

    public UserServiceImpl() {
        System.out.println("Initializing user service...");
    }

    public List<User> findUsersByPropertyValue(String propertyName, String propertyValue) {
        return new ArrayList<User>();
    }

    public boolean save(User user) {
        return false;
    }
}
