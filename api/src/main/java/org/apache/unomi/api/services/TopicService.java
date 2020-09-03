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
package org.apache.unomi.api.services;

import org.apache.unomi.api.PartialList;
import org.apache.unomi.api.Topic;
import org.apache.unomi.api.query.Query;

public interface TopicService {

    /**
     * Retrieves the topic identified by the specified identifier.
     *
     * @param topicId the identifier of the topic to retrieve
     * @return the topic identified by the specified identifier or {@code null} if no such topic exists
     */
    Topic load(final String topicId);

    /**
     * Saves the specified topic in the context server.
     *
     * @param topic the topic to be saved
     * @return the newly saved topic if the creation or update was successful, {@code null} otherwise
     */
    Topic save(final Topic topic);

    /**
     * Retrieves topic matching the specified query.
     *
     * @param query a {@link Query} specifying which elements to retrieve
     * @return a {@link PartialList} of {@link Topic} metadata
     */
    PartialList<Topic> search(final Query query);

    /**
     * Removes the topic identified by the specified identifier.
     *
     * @param topicId the identifier of the profile or persona to delete
     * @return {@code true} if the deletion was successful, {@code false} otherwise
     */
    boolean delete(final String topicId);

}
