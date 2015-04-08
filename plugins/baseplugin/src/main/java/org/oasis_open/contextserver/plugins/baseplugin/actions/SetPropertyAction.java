package org.oasis_open.contextserver.plugins.baseplugin.actions;

/*
 * #%L
 * context-server-services
 * $Id:$
 * $HeadURL:$
 * %%
 * Copyright (C) 2014 - 2015 Jahia Solutions
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import org.apache.commons.beanutils.BeanUtils;
import org.oasis_open.contextserver.api.Event;
import org.oasis_open.contextserver.api.actions.Action;
import org.oasis_open.contextserver.api.actions.ActionExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.text.SimpleDateFormat;
import java.util.TimeZone;

public class SetPropertyAction implements ActionExecutor {
    private static final Logger logger = LoggerFactory.getLogger(SetPropertyAction.class.getName());

    public SetPropertyAction() {
    }

    public String getActionId() {
        return "setPropertyAction";
    }

    public boolean execute(Action action, Event event) {
        Object propertyValue = action.getParameterValues().get("setPropertyValue");
        if (propertyValue != null && propertyValue.equals("now")) {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
            format.setTimeZone(TimeZone.getTimeZone("UTC"));
            propertyValue = format.format(event.getTimeStamp());
        }
        boolean modified = false;
        String propertyName = (String) action.getParameterValues().get("setPropertyName");
        try {
            if (Boolean.TRUE.equals(action.getParameterValues().get("storeInSession"))) {
                if (propertyValue != null && !propertyValue.equals(BeanUtils.getProperty(event.getSession(), propertyName))) {
                    BeanUtils.setProperty(event.getSession(), propertyName, propertyValue);
                    modified = true;
                }
            } else {
                if (propertyValue != null && !propertyValue.equals(BeanUtils.getProperty(event.getProfile(), propertyName))) {
                    BeanUtils.setProperty(event.getProfile(), propertyName, propertyValue);
                    modified = true;
                }
            }
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            logger.error("Cannot set property", e);
        }
        return modified;
    }

}
