package org.oasis_open.wemi.context.server.impl.services;

import org.oasis_open.wemi.context.server.api.Session;
import org.oasis_open.wemi.context.server.api.User;
import org.oasis_open.wemi.context.server.api.services.UserService;
import org.oasis_open.wemi.context.server.persistence.spi.PersistenceService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by loom on 24.04.14.
 */
public class UserServiceImpl implements UserService {

    private PersistenceService persistenceService;

    public void setPersistenceService(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    public UserServiceImpl() {
        System.out.println("Initializing user service...");
    }

    public List<User> findUsersByPropertyValue(String propertyName, String propertyValue) {
        return new ArrayList<User>();
    }

    public User load(String userId) {
        return persistenceService.load(userId, User.USER_ITEM_TYPE, User.class);
    }

    public boolean save(User user) {
        persistenceService.save(user);
        return false;
    }

    public List<String> getUserProperties() {
        Map<String,Map<String,String>> mappings = persistenceService.getMapping(User.USER_ITEM_TYPE);
        return new ArrayList<String>(mappings.keySet());
    }

    public Session loadSession(String sessionId) {
        return persistenceService.load(sessionId, Session.SESSION_ITEM_TYPE, Session.class);
    }

    public boolean saveSession(Session event) {
        persistenceService.save(event);
        return false;
    }


}
