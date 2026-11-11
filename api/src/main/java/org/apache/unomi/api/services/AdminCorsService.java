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
package org.apache.unomi.api.services;

/**
 * Origins that may use credentialed CORS against the admin REST API and GraphQL. The default list is
 * empty: cross-origin browsers do not get {@code Access-Control-Allow-Credentials} on those surfaces
 * until the operator lists the admin UI origin.
 */
public interface AdminCorsService {

    /**
     * Whether a browser {@code Origin} is listed for credentialed admin CORS.
     *
     * @param origin the request {@code Origin} header, may be {@code null}
     * @return {@code true} only when the origin is present and listed
     */
    boolean allows(String origin);
}
