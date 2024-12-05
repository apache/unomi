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
package org.apache.unomi.persistence.opensearch;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.unomi.api.Event;
import org.apache.unomi.api.Item;
import org.apache.unomi.persistence.spi.CustomObjectMapper;

/**
 * This CustomObjectMapper is used to avoid the version parameter to be registered in ES
 * @author dgaillard
 */
public class OSCustomObjectMapper extends CustomObjectMapper {

    private static final long serialVersionUID = -5017620674440085575L;

    public OSCustomObjectMapper() {
        super();
        this.addMixIn(Item.class, OSItemMixIn.class);
        this.addMixIn(Event.class, OSEventMixIn.class);
    }

    public static ObjectMapper getObjectMapper() {
        return OSCustomObjectMapper.Holder.INSTANCE;
    }

    private static class Holder {
        static final OSCustomObjectMapper INSTANCE = new OSCustomObjectMapper();
    }
}
