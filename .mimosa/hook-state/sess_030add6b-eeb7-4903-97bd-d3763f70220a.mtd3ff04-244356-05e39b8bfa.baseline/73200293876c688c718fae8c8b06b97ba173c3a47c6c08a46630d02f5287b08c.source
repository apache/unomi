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
 * limitations under the License
 */
package org.apache.unomi.itests.persistence;

/**
 * Marker category for ITs whose <em>primary</em> assertions need HTTP admin APIs
 * (snapshot restore, index rollover policy HTTP). Prefer keeping these classes in
 * {@link org.apache.unomi.itests.CorePersistenceITs} / {@link org.apache.unomi.itests.AllITs}
 * and gating with {@link org.junit.Assume} on {@link PersistenceITCapabilities} so unsupported
 * backends skip rather than losing suite membership.
 * <p>
 * Optional Failsafe {@code excludedGroups} may still list this category for specialized cells.
 */
public interface SearchBackendIT {
}
