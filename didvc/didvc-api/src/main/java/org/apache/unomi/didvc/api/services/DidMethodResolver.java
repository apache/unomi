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

package org.apache.unomi.didvc.api.services;

import org.apache.unomi.didvc.api.DidDocumentData;

/**
 * DID-method resolution SPI, following the Universal Resolver driver
 * pattern: one adapter per DID method ({@code did:web}, {@code did:key},
 * {@code did:iamsmart}, {@code did:realdid}, …). The
 * {@link UniversalDidResolverService} aggregates adapters and dispatches
 * resolution by the DID's method segment.
 */
public interface DidMethodResolver {

    /**
     * The DID method this resolver handles, e.g. {@code web}, {@code key},
     * {@code iamsmart} or {@code realdid}.
     *
     * @return the DID method name
     */
    String getMethod();

    /**
     * Resolves a DID of this adapter's method to its DID document.
     *
     * @param did the DID to resolve
     * @return the DID document, or null when the DID is unknown
     */
    DidDocumentData resolve(String did);
}
