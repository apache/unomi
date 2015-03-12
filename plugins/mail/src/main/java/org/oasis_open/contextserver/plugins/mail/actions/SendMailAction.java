package org.oasis_open.contextserver.plugins.mail.actions;

/*
 * #%L
 * Context Server Plugin - Mail notifications actions
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

import org.apache.commons.mail.DefaultAuthenticator;
import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.HtmlEmail;
import org.apache.commons.mail.ImageHtmlEmail;
import org.oasis_open.contextserver.api.Event;
import org.oasis_open.contextserver.api.actions.Action;
import org.oasis_open.contextserver.api.actions.ActionExecutor;
import org.stringtemplate.v4.ST;

import java.net.MalformedURLException;
import java.net.URL;

public class SendMailAction implements ActionExecutor {

    private String mailServerHostName;
    private int mailServerPort;
    private String mailServerUsername;
    private String mailServerPassword;
    private boolean mailServerSSLOnConnect = true;

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

    public boolean execute(Action action, Event event) {
        String from = (String) action.getParameterValues().get("from");
        String to = (String) action.getParameterValues().get("to");
        String cc = (String) action.getParameterValues().get("cc");
        String bcc = (String) action.getParameterValues().get("bcc");
        String subject = (String) action.getParameterValues().get("subject");
        String template = (String) action.getParameterValues().get("template");

        ST stringTemplate = new ST(template);
        stringTemplate.add("profile", event.getProfile());
        stringTemplate.add("event", event);
        // load your HTML email template
        String htmlEmailTemplate = stringTemplate.render();

        // define you base URL to resolve relative resource locations
        URL url = null;
        try {
            url = new URL("http://www.apache.org");
        } catch (MalformedURLException e) {
            e.printStackTrace();
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
            e.printStackTrace();
        }

        return true;
    }
}
