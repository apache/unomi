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

package org.apache.unomi.services.common.security;

/**
 * Utility class for security-related helpers, such as safe logging of sensitive values.
 */
public class SecurityUtils {

    private static final String MASK_SUFFIX = "****";
    private static final int VISIBLE_PREFIX_LENGTH = 4;

    /**
     * Masks a secret value for safe use in log statements.
     * Shows the first {@value #VISIBLE_PREFIX_LENGTH} characters followed by {@code ****},
     * or {@code ****} entirely if the secret is null or too short to reveal safely.
     *
     * @param secret the secret to mask (e.g. an API key or shared token)
     * @return a masked representation safe for logging
     */
    public static String maskSecret(String secret) {
        if (secret == null || secret.length() <= VISIBLE_PREFIX_LENGTH) {
            return MASK_SUFFIX;
        }
        return secret.substring(0, VISIBLE_PREFIX_LENGTH) + MASK_SUFFIX;
    }
}
