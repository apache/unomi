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

/**
 * Persistence API for {@link Topic} items.
 * Supports load, save, search, and delete of topics used to categorize
 * entities across the context server.
 */
public interface TopicService {

    /**
     * Loads a topic by id.
     *
     * @param topicId topic identifier
     * @return matching topic, or {@code null} if none exists
     */
    Topic load(final String topicId);

    /**
     * Persists a topic.
     *
     * @param topic topic to save
     * @return saved topic, or {@code null} if the operation failed
     */
    Topic save(final Topic topic);

    /**
     * Searches topics using a structured query.
     *
     * @param query search query
     * @return matching topics
     */
    PartialList<Topic> search(final Query query);

    /**
     * Deletes a topic by id.
     *
     * @param topicId topic identifier
     * @return {@code true} if deletion succeeded
     */
    boolean delete(final String topicId);

}
