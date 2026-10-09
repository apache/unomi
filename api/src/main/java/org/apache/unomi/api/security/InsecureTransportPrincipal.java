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
package org.apache.unomi.api.security;

import java.security.Principal;

/**
 * Marks a request subject whose credentials arrived over a connection that was not encrypted.
 * <p>
 * It grants nothing. It is recorded by {@link SecurityService#recordRequestTransport} so that code
 * guarding a sensitive operation, such as a profile merge, can refuse a caller that is otherwise
 * trusted when the deployment requires a secure transport.
 */
public final class InsecureTransportPrincipal implements Principal {

    /** The single instance: the marker carries no state. */
    public static final InsecureTransportPrincipal INSTANCE = new InsecureTransportPrincipal();

    private InsecureTransportPrincipal() {
    }

    @Override
    public String getName() {
        return "insecure-transport";
    }

    @Override
    public String toString() {
        return "InsecureTransportPrincipal";
    }
}
