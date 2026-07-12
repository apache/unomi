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

import java.util.Date;

/**
 * {@link Item} that records when it was created or last modified.
 * Event and profile-like types implement this interface so retention,
 * ordering, and auditing can use consistent timestamps.
 */
public interface TimestampedItem {
    /**
     * Creation or last-modification timestamp for timestamped items.
     *
     * @return the timestamp
     */
    Date getTimeStamp();
}
