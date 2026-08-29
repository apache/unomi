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
 * Universal DID resolution: dispatches to the method-specific
 * {@link DidMethodResolver} adapters ({@code did:web} through the platform
 * {@link DidService}, {@code did:key} derived from the embedded key, iAM
 * Smart / RealDID through their configured drivers) and falls back to the
 * persisted DID-document registry, which doubles as the local cache and as
 * the stub-document source for methods without a live driver.
 */
public interface UniversalDidResolverService {

    /**
     * Resolves a DID of any supported method to its DID document.
     *
     * @param did the DID to resolve
     * @return the DID document, or null when the DID is unknown
     */
    DidDocumentData resolve(String did);
}
