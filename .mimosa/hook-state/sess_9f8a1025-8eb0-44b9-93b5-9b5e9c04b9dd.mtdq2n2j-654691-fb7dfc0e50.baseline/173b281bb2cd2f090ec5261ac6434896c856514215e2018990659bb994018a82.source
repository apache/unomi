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

import org.apache.unomi.didvc.api.items.CredentialRecord;

import java.util.Date;

/**
 * Credential re-verification lifecycle: marks credentials as refresh-due
 * when they approach expiry (annual-refresh pattern) or when the subject's
 * identity evidence changes (e.g. SIM re-registration), and sweeps for
 * due credentials on a schedule.
 */
public interface CredentialRefreshService {

    /**
     * The refresh window applied before expiry (90 days by default).
     */
    long DEFAULT_REFRESH_WINDOW_MILLIS = 90L * 24 * 60 * 60 * 1000;

    /**
     * Checks whether a credential is inside its re-verification window or
     * has been marked refresh-due.
     *
     * @param record the credential record
     * @param now    the current time
     * @return true when re-verification is due
     */
    boolean isRefreshDue(CredentialRecord record, Date now);

    /**
     * Marks all of a subject's credentials refresh-due after an identity
     * change (e.g. SIM re-registration).
     *
     * @param subjectId the subject
     * @return the number of credentials marked
     */
    int markRefreshDueForSubject(String subjectId);

    /**
     * Sweeps credential records and marks those entering the refresh window.
     *
     * @param now the current time
     * @return the number of credentials newly marked refresh-due
     */
    int sweepExpiringCredentials(Date now);
}
