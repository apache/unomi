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

import org.apache.unomi.didvc.edge.platform.PlatformApi;
import org.apache.unomi.didvc.edge.support.InMemoryPlatformApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

/**
 * Demo/local-interop configuration: runs the credential edge against an
 * in-memory platform that issues genuine SD-JWT credentials, so the full
 * OID4VCI/OID4VP surface can be exercised by real wallets without a Unomi
 * deployment. Activate with {@code --spring.profiles.active=demo}.
 */
@Configuration
@Profile("demo")
public class DemoPlatformConfiguration {

    @Bean
    @Primary
    public InMemoryPlatformApi demoPlatformApi() {
        InMemoryPlatformApi platformApi = new InMemoryPlatformApi();
        // Demo trust: the demo verifier tenant accepts the demo issuer's
        // KYC credential, so OID4VP verification succeeds end to end.
        platformApi.trust("bank-a", InMemoryPlatformApi.ISSUER_DID, "hkt_kyc_v1");
        return platformApi;
    }

    /**
     * The demo issuer's kid, for building offers.
     *
     * @return the issuer kid
     */
    @Bean
    public String demoIssuerKid(PlatformApi platformApi) {
        return ((InMemoryPlatformApi) platformApi).getIssuerKid();
    }
}
