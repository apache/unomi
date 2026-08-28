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

package org.apache.unomi.didvc.edge.config;

import org.apache.unomi.didvc.audit.AuditLogService;
import org.apache.unomi.didvc.audit.InMemoryAuditLogStore;
import org.apache.unomi.didvc.edge.store.InMemoryNonceStore;
import org.apache.unomi.didvc.edge.store.NonceStore;
import org.apache.unomi.didvc.edge.store.RedisNonceStore;
import org.apache.unomi.didvc.metering.InMemoryMeteringSink;
import org.apache.unomi.didvc.metering.MeteringService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Edge-local services: the immutable audit log, the verification
 * metering service, and the presentation nonce store. Defaults are
 * in-memory; enabling {@code didvc.edge.redis-enabled=true} swaps the
 * nonce store for Redis (SET NX / GETDEL) so replay protection spans a
 * scaled verifier fleet. Production also swaps the audit/metering stores
 * for the JDBC audit store (PostgreSQL) and the Kafka metering sink.
 */
@Configuration
public class DidvcEdgeConfiguration {

    /**
     * OAuth/OID4VCI endpoint responses (metadata, tokens, credentials,
     * status lists) must never be cached by intermediaries — RFC 6749
     * §5.1 and the OID4VCI metadata requirements.
     */
    @Bean
    public org.springframework.boot.web.servlet.FilterRegistrationBean<jakarta.servlet.Filter> noStoreCacheControlFilter() {
        org.springframework.boot.web.servlet.FilterRegistrationBean<jakarta.servlet.Filter> registration =
                new org.springframework.boot.web.servlet.FilterRegistrationBean<>();
        registration.setFilter((request, response, chain) -> {
            if (response instanceof jakarta.servlet.http.HttpServletResponse httpResponse) {
                httpResponse.setHeader("Cache-Control", "no-store");
            }
            chain.doFilter(request, response);
        });
        registration.addUrlPatterns("/*");
        registration.setOrder(org.springframework.core.Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }

    @Bean
    public AuditLogService auditLogService() {
        return new AuditLogService(new InMemoryAuditLogStore());
    }

    @Bean
    public InMemoryMeteringSink meteringSink() {
        return new InMemoryMeteringSink();
    }

    @Bean
    public MeteringService meteringService(InMemoryMeteringSink meteringSink) {
        return new MeteringService(meteringSink);
    }

    @Bean
    public InMemoryNonceStore inMemoryNonceStore() {
        return new InMemoryNonceStore();
    }

    /**
     * Redis-backed nonce store, active when
     * {@code didvc.edge.redis-enabled=true} and a Redis connection is
     * configured via {@code spring.data.redis.*}.
     */
    @Bean
    @Primary
    @ConditionalOnProperty(name = "didvc.edge.redis-enabled", havingValue = "true")
    public NonceStore redisNonceStore(StringRedisTemplate redis) {
        return new RedisNonceStore(redis);
    }
}
