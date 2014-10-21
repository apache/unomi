package org.oasis_open.wemi.context.server.impl.actions;

import org.oasis_open.wemi.context.server.api.Event;
import org.oasis_open.wemi.context.server.api.Persona;
import org.oasis_open.wemi.context.server.api.Session;
import org.oasis_open.wemi.context.server.api.User;
import org.oasis_open.wemi.context.server.api.actions.Action;
import org.oasis_open.wemi.context.server.api.actions.ActionExecutor;
import org.oasis_open.wemi.context.server.api.services.UserService;

import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;

/**
 * Created by loom on 16.10.14.
 */
public class MergeProfilesOnPropertyAction implements ActionExecutor {

    private final int MAX_COOKIE_AGE_IN_SECONDS = 60 * 60 * 24 * 365 * 10; // 10-years
    private int cookieAgeInSeconds = MAX_COOKIE_AGE_IN_SECONDS;
    private String profileIdCookieName = "context-profile-id";

    private UserService userService;

    public void setCookieAgeInSeconds(int cookieAgeInSeconds) {
        this.cookieAgeInSeconds = cookieAgeInSeconds;
    }

    public void setProfileIdCookieName(String profileIdCookieName) {
        this.profileIdCookieName = profileIdCookieName;
    }

    public void setUserService(UserService userService) {
        this.userService = userService;
    }

    public boolean execute(Action action, Event event) {
        String mergeProfilePropertyName = (String) action.getParameterValues().get("mergeProfilePropertyName");
        User user = event.getUser();

        if (user instanceof Persona) {
            return false;
        }

        Object currentMergePropertyValue = user.getProperty(mergeProfilePropertyName);

        User masterUser = userService.mergeUsersOnProperty(user, event.getSession(), mergeProfilePropertyName, currentMergePropertyValue.toString());

        if (masterUser == null) {
            return false;
        }

        if (!masterUser.getId().equals(user.getId())) {
            HttpServletResponse httpServletResponse = (HttpServletResponse) event.getAttributes().get(Event.HTTP_RESPONSE_ATTRIBUTE);
            sendProfileCookie(masterUser, httpServletResponse);
            Session session = event.getSession();
            if (!session.getUserId().equals(masterUser.getId())) {
                session.setUser(masterUser);
                userService.saveSession(session);
            }
            userService.delete(user.getId(), false);
        }

        return true;
    }

    public void sendProfileCookie(User user, ServletResponse response) {
        if (response instanceof HttpServletResponse) {
            HttpServletResponse httpServletResponse = (HttpServletResponse) response;
            Cookie visitorIdCookie = new Cookie(profileIdCookieName, user.getItemId());
            visitorIdCookie.setPath("/");
            visitorIdCookie.setMaxAge(cookieAgeInSeconds);
            httpServletResponse.addCookie(visitorIdCookie);
        }
    }

}
