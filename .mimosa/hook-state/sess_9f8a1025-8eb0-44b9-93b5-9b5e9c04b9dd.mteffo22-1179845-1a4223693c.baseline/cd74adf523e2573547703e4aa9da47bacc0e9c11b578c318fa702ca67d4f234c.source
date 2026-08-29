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
package org.apache.unomi.api;

import org.apache.unomi.api.services.PersonalizationService;

import java.util.List;

/**
 * Interface for personalization strategies. Will filter and reorder the content list according to the strategy
 * implementation
 */
public interface PersonalizationStrategy {

    /**
     * Filters and personalizes the list of contents passed as a parameter using the strategy's implementation.
     * @param profile the profile to use for the personalization
     * @param session the session to use for the personalization
     * @param personalizationRequest the request contains the contents to personalizes as well as the parameters for the
     *                               strategy (options)
     * @return the personalization result that contains the list of content IDs resulting from the filtering/re-ordering
     */
    PersonalizationResult personalizeList(Profile profile, Session session, PersonalizationService.PersonalizationRequest personalizationRequest);
}
