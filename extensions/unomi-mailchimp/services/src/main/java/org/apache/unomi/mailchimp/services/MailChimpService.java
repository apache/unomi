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

package org.apache.unomi.mailchimp.services;


import org.apache.unomi.api.Profile;
import org.apache.unomi.api.actions.Action;

import java.util.HashMap;
import java.util.List;

public interface MailChimpService {

    /**
     * This function will get all MailChimp lists.
     *
     * @return a List of MailChimp lists with the name and the id.
     */
    List<HashMap<String, String>> getAllLists();

    /**
     * This function will add the current visitor to a MailChimp list.
     *
     * @param profile the Unomi profile to add to the list @see org.apache.unomi.api.Profile
     * @param action the action used to call this method, to retrieve parameters @see org.apache.unomi.api.actions.Action
     * @return true if the visitor is successfully added to a MailChimp list.
     */
    MailChimpResult addToMCList(Profile profile, Action action);

    /**
     * This function will remove the current visitor from a MailChimp list.
     *
     * @param profile the Unomi profile to remove from the list @see org.apache.unomi.api.Profile
     * @param action the action used to call this method, to retrieve parameters @see org.apache.unomi.api.actions.Action
     * @return true if the visitor is successfully removed to a MailChimp list.
     */
    MailChimpResult removeFromMCList(Profile profile, Action action);

    /**
     * This function will unsbscribe the current visitor to a MailChimp list.
     *
     * @param profile the Unomi profile to unsubscribe from the list @see org.apache.unomi.api.Profile
     * @param action the action used to call this method, to retrieve parameters @see org.apache.unomi.api.actions.Action
     * @return true if the visitor is successfully unsbscribed to a MailChimp list.
     */
    MailChimpResult unsubscribeFromMCList(Profile profile, Action action);

    /**
     * This function will update merge properties of MailChimp list.
     *
     * @param profile the Unomi profile to unsubscribe from the list @see org.apache.unomi.api.Profile
     * @param action the action used to call this method, to retrieve parameters @see org.apache.unomi.api.actions.Action
     * @return true if the visitor is successfully unsbscribed to a MailChimp list.
     */
    MailChimpResult updateMCProfileProperties(Profile profile, Action action);
}


