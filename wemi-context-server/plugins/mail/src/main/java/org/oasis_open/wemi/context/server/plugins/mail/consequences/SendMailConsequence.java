package org.oasis_open.wemi.context.server.plugins.mail.consequences;

import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.HtmlEmail;
import org.apache.commons.mail.ImageHtmlEmail;
import org.oasis_open.wemi.context.server.api.Event;
import org.oasis_open.wemi.context.server.api.User;
import org.oasis_open.wemi.context.server.api.consequences.Consequence;
import org.oasis_open.wemi.context.server.api.consequences.ConsequenceExecutor;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * Created by loom on 14.08.14.
 */
public class SendMailConsequence implements ConsequenceExecutor {

    private String mailServerHostName;

    public void setMailServerHostName(String mailServerHostName) {
        this.mailServerHostName = mailServerHostName;
    }

    public boolean execute(Consequence consequence, Event event) {
        String from = (String) consequence.getParameterValues().get("from");
        String to = (String) consequence.getParameterValues().get("to");
        String cc = (String) consequence.getParameterValues().get("cc");
        String bcc = (String) consequence.getParameterValues().get("bcc");
        String subject = (String) consequence.getParameterValues().get("subject");
        String template = (String) consequence.getParameterValues().get("template");

        // load your HTML email template
        String htmlEmailTemplate = template;

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
