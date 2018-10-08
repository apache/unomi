/*

        ~ Licensed to the Apache Software Foundation (ASF) under one or more
        ~ contributor license agreements.  See the NOTICE file distributed with
        ~ this work for additional information regarding copyright ownership.
        ~ The ASF licenses this file to You under the Apache License, Version 2.0
        ~ (the "License"); you may not use this file except in compliance with
        ~ the License.  You may obtain a copy of the License at
        ~
        ~      http://www.apache.org/licenses/LICENSE-2.0
        ~
        ~ Unless required by applicable law or agreed to in writing, software
        ~ distributed under the License is distributed on an "AS IS" BASIS,
        ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
        ~ See the License for the specific language governing permissions and
        ~ limitations under the License.
*/

package org.apache.unomi.twilio.actions;

import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Call;
import com.twilio.type.PhoneNumber;
import org.apache.unomi.api.Event;
import org.apache.unomi.api.actions.Action;
import org.apache.unomi.api.actions.ActionExecutor;
import org.apache.unomi.api.services.EventService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

/**
 * @author dgaillard
 */
public class TwilioCallAction implements ActionExecutor {
    private static Logger logger = LoggerFactory.getLogger(TwilioCallAction.class);

//    public static final String DEMO_VOICE_URL = "http://demo.twilio.com/docs/voice.xml";
    public static final String DEMO_VOICE_URL = "http://6cebf511.eu.ngrok.io/tracker/voice.xml";
    private static final String VISITOR_PHONE_NUMBER = "visitorPhoneNumber";

    private String twilioAccountSid;
    private String twilioAuthToken;
    private String twilioPhoneNumber;

    @Override public int execute(Action action, Event event) {
        if (logger.isDebugEnabled()) {
            logger.debug("Execute action {} for event id {} and type {}", TwilioCallAction.class.getName(), event.getItemId(), event.getEventType());
        }

        Map<String, Object> parameterValues = action.getParameterValues();
        if (!parameterValues.containsKey(VISITOR_PHONE_NUMBER)) {
            logger.warn("Could not execute action, missing mandatory parameter {}, event id = {}", VISITOR_PHONE_NUMBER, event.getItemId());
            return EventService.NO_CHANGE;
        }

        Twilio.init(twilioAccountSid, twilioAuthToken);

        URI textUrl;
        try {
            textUrl = new URI(DEMO_VOICE_URL);
        } catch (URISyntaxException e) {
            logger.error("Error when building URI with response URL = {}", "http://localhost:8181/cxs/twilio/validate");
            return EventService.NO_CHANGE;
        }

        Call call = Call.creator(new PhoneNumber(parameterValues.get(VISITOR_PHONE_NUMBER).toString()), new PhoneNumber(twilioPhoneNumber), textUrl).create();

        logger.info(call.getSid());

        if (logger.isDebugEnabled()) {
            logger.debug("Action {} is done for event id {} and type {}", TwilioCallAction.class.getName(), event.getItemId(), event.getEventType());
        }
        return EventService.NO_CHANGE;
    }

    public void setTwilioAccountSid(String twilioAccountSid) {
        this.twilioAccountSid = twilioAccountSid;
    }

    public void setTwilioAuthToken(String twilioAuthToken) {
        this.twilioAuthToken = twilioAuthToken;
    }

    public void setTwilioPhoneNumber(String twilioPhoneNumber) {
        this.twilioPhoneNumber = twilioPhoneNumber;
    }
}
