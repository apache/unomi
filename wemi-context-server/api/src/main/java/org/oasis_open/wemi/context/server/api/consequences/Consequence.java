package org.oasis_open.wemi.context.server.api.consequences;

import org.oasis_open.wemi.context.server.api.User;

/**
 * Created by toto on 26/06/14.
 */
public interface Consequence {
    public boolean apply(User user);
}
