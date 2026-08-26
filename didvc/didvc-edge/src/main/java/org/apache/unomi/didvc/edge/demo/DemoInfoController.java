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

package org.apache.unomi.didvc.edge.demo;

import org.apache.unomi.didvc.edge.support.InMemoryPlatformApi;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Demo-only endpoint exposing the in-memory issuer's kid so wallets can
 * build credential offers during local interop testing.
 */
@RestController
@Profile("demo")
public class DemoInfoController {

    private final InMemoryPlatformApi platformApi;

    public DemoInfoController(InMemoryPlatformApi platformApi) {
        this.platformApi = platformApi;
    }

    @GetMapping("/demo/issuer-kid")
    public Map<String, Object> issuerKid() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("kid", platformApi.getIssuerKid());
        result.put("issuerDid", InMemoryPlatformApi.ISSUER_DID);
        return result;
    }

    /**
     * The demo issuer's public JWK, so wallets can independently verify
     * received credentials.
     */
    @GetMapping("/demo/issuer-jwk")
    public Map<String, Object> issuerJwk() {
        return platformApi.getIssuerKey().toPublicJWK().toJSONObject();
    }
}
