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

import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

/**
 * Redis-backed nonce store: issue via SET NX with TTL, consume via
 * GETDEL — atomic single-use semantics shared across every verifier
 * instance in a scaled fleet.
 */
public class RedisNonceStore implements NonceStore {

    private static final String KEY_PREFIX = "didvc:nonce:";

    private final StringRedisTemplate redis;

    public RedisNonceStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void issue(String key, long ttlSeconds) {
        redis.opsForValue().setIfAbsent(KEY_PREFIX + key, "1", Duration.ofSeconds(ttlSeconds));
    }

    @Override
    public boolean consume(String key) {
        return redis.opsForValue().getAndDelete(KEY_PREFIX + key) != null;
    }
}
