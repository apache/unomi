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

package org.apache.unomi.mailchimp.actions;

import org.apache.unomi.api.Event;
import org.apache.unomi.api.actions.Action;
import org.apache.unomi.api.actions.ActionExecutor;
import org.apache.unomi.api.services.EventService;
import org.apache.unomi.mailchimp.services.MailChimpResult;
import org.apache.unomi.mailchimp.services.MailChimpService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RemoveVisitorFromMailChimpListsAction implements ActionExecutor {
    private static Logger logger = LoggerFactory.getLogger(RemoveVisitorFromMailChimpListsAction.class);
    private MailChimpService mailChimpService;

    public void setMailChimpService(MailChimpService mailChimpService) {
        this.mailChimpService = mailChimpService;
    }

    @Override
    public int execute(Action action, Event event) {
        MailChimpResult result = mailChimpService.removeFromMCList(event.getProfile(), action);
        switch (result) {

            case REMOVED:
                logger.info("The visitor has been successfully removed from MailChimp list");
                break;
            case NO_CHANGE:
                logger.info("Visitor was not part of the list");
                break;
            default:
                break;
        }
        return EventService.NO_CHANGE;

    }
}
