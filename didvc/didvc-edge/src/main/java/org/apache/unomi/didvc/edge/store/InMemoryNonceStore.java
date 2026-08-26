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

package org.apache.unomi.didvc.edge.store;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory nonce store with expiry. Suitable for a single verifier
 * instance; use the Redis implementation when the edge is load-balanced.
 */
public class InMemoryNonceStore implements NonceStore {

    private final Map<String, Long> nonces = new ConcurrentHashMap<>();

    @Override
    public void issue(String key, long ttlSeconds) {
        nonces.put(key, System.currentTimeMillis() + ttlSeconds * 1000L);
    }

    @Override
    public boolean consume(String key) {
        Long expiry = nonces.remove(key);
        if (expiry == null) {
            return false;
        }
        return expiry >= System.currentTimeMillis();
    }
}
