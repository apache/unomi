/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.unomi.plugins.mail.actions;

import org.apache.commons.mail.DefaultAuthenticator;
import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.HtmlEmail;
import org.apache.commons.mail.ImageHtmlEmail;
import org.apache.unomi.api.Event;
import org.apache.unomi.api.Profile;
import org.apache.unomi.api.actions.Action;
import org.apache.unomi.api.actions.ActionExecutor;
import org.apache.unomi.api.services.EventService;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.stringtemplate.v4.ST;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class SendMailAction implements ActionExecutor {
    private static final Logger logger = LoggerFactory.getLogger(SendMailAction.class.getName());

    private PersistenceService persistenceService;

    private String mailServerHostName;
    private int mailServerPort;
    private String mailServerUsername;
    private String mailServerPassword;
    private boolean mailServerSSLOnConnect = true;

    public void setPersistenceService(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    public void setMailServerHostName(String mailServerHostName) {
        this.mailServerHostName = mailServerHostName;
    }

    public void setMailServerPort(int mailServerPort) {
        this.mailServerPort = mailServerPort;
    }

    public void setMailServerUsername(String mailServerUsername) {
        this.mailServerUsername = mailServerUsername;
    }

    public void setMailServerPassword(String mailServerPassword) {
        this.mailServerPassword = mailServerPassword;
    }

    public void setMailServerSSLOnConnect(boolean mailServerSSLOnConnect) {
        this.mailServerSSLOnConnect = mailServerSSLOnConnect;
    }

    public int execute(Action action, Event event) {
        String notifType = (String) action.getParameterValues().get("notificationType");
        String notifTypeId = (String) action.getParameterValues().get("notificationTypeId");
        Boolean notifyOnce = (Boolean) action.getParameterValues().get("notifyOncePerProfile");
        String from = (String) action.getParameterValues().get("from");
        String to = (String) action.getParameterValues().get("to");
        String cc = (String) action.getParameterValues().get("cc");
        String bcc = (String) action.getParameterValues().get("bcc");
        String subject = (String) action.getParameterValues().get("subject");
        String template = (String) action.getParameterValues().get("template");

        if (notifType == null) {
            notifType = "default";
        }
        if (notifTypeId == null) {
            notifTypeId = subject;
        }
        if (notifyOnce == null) {
            notifyOnce = false;
        }

        Map profileNotif = (HashMap) event.getProfile().getSystemProperties().get("notificationAck");
        if (profileNotif != null && profileNotif.get(notifType) != null && ((HashMap) profileNotif.get(notifType)).get(notifTypeId) != null) {
            Integer notifTypeAck = (Integer) ((HashMap) profileNotif.get(notifType) ).get(notifTypeId);
            if(notifyOnce.booleanValue() && notifTypeAck > 0){
                logger.info("Notification "+notifType+" already sent for the profile "+event.getProfileId());
                return EventService.NO_CHANGE;
            }else{
                ((HashMap) profileNotif.get(notifType) ).put(notifTypeId, notifTypeAck+1);
            }
        } else {
            if(profileNotif == null){
                profileNotif = new HashMap();
            }
            profileNotif.put(notifType, profileNotif.get(notifType)!=null?profileNotif.get(notifType):new HashMap());
            Integer notifTypeAck = (Integer) ((HashMap) profileNotif.get(notifType) ).get(notifTypeId);
            if(notifTypeAck == null){
                ((HashMap) profileNotif.get(notifType) ).put(notifTypeId, 1);
            }
        }

        event.getProfile().setSystemProperty("notificationAck", profileNotif);
        event.getProfile().setSystemProperty("lastUpdated", new Date());

        persistenceService.update(event.getProfile().getItemId(), null, Profile.class, "systemProperties", event.getProfile().getSystemProperties());

        ST stringTemplate = new ST(template, '$', '$');
        stringTemplate.add("profile", event.getProfile());
        stringTemplate.add("event", event);
        // load your HTML email template
        String htmlEmailTemplate = stringTemplate.render();

        // define you base URL to resolve relative resource locations
        try {
            new URL("http://www.apache.org");
        } catch (MalformedURLException e) {
            //
        }

        // create the email message
        HtmlEmail email = new ImageHtmlEmail();
        // email.setDataSourceResolver(new DataSourceResolverImpl(url));
        email.setHostName(mailServerHostName);
        email.setSmtpPort(mailServerPort);
        email.setAuthenticator(new DefaultAuthenticator(mailServerUsername, mailServerPassword));
        email.setSSLOnConnect(mailServerSSLOnConnect);
        try {
            email.addTo(to);
            email.setFrom(from);
            if (cc != null && cc.length() > 0) {
                email.addCc(cc);
            }
            if (bcc != null && bcc.length() > 0) {
                email.addBcc(bcc);
            }
            email.setSubject(subject);

            // set the html message
            email.setHtmlMsg(htmlEmailTemplate);

            // set the alternative message
            email.setTextMsg("Your email client does not support HTML messages");

            // send the email
            email.send();
        } catch (EmailException e) {
            logger.error("Cannot send mail",e);
        }

        return EventService.NO_CHANGE;
    }
}
