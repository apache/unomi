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

/**
 * Single-use nonce store for presentation anti-replay protection. Nonces
 * are issued by the verifier, consumed exactly once at presentation time,
 * and expire after a TTL. The in-memory implementation serves single
 * instances; the Redis implementation keeps replay protection intact
 * across a horizontally scaled verifier fleet.
 */
public interface NonceStore {

    /**
     * Issues a nonce under a key, valid for the given TTL.
     *
     * @param key       the nonce key (e.g. {@code <tenant>:<nonce>})
     * @param ttlSeconds the time to live in seconds
     */
    void issue(String key, long ttlSeconds);

    /**
     * Consumes a nonce. Each nonce can be consumed at most once.
     *
     * @param key the nonce key
     * @return true when the nonce existed and was consumed
     */
    boolean consume(String key);

    /**
     * Checks whether a nonce is currently valid without consuming it. Used
     * to accept a proof nonce before the rest of the proof validates; the
     * caller consumes the nonce once the proof is fully accepted.
     *
     * @param key the nonce key
     * @return true when the nonce exists and has not expired
     */
    default boolean contains(String key) {
        return false;
    }
}
