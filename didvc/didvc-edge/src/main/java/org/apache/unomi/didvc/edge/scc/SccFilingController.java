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

package org.apache.unomi.didvc.edge.scc;

import org.apache.unomi.didvc.scc.SccFilingExport;
import org.apache.unomi.didvc.scc.SccFilingExporter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * GBA SCC filing-export API (FR-D4): renders the audit-log verification
 * records for a data-flow counterparty into the filing-template field
 * set the operator submits for its continuing bilateral-filing
 * obligations. The export carries claim type names and outcomes only —
 * never personal data.
 */
@RestController
public class SccFilingController {

    private static final long DEFAULT_WINDOW_MILLIS = 30L * 24 * 60 * 60 * 1000;

    private final SccFilingExporter exporter;

    public SccFilingController(SccFilingExporter exporter) {
        this.exporter = exporter;
    }

    /**
     * The filing export for the counterparty tenant over a time window
     * (millis since epoch; defaults to the last 30 days).
     */
    @GetMapping("/{tenantId}/scc/filing-export")
    public SccFilingExport filingExport(@PathVariable("tenantId") String tenantId,
                                        @RequestParam(value = "contract_reference", required = false) String contractReference,
                                        @RequestParam(value = "purpose", required = false) String purpose,
                                        @RequestParam(value = "from", required = false) Long fromMillis,
                                        @RequestParam(value = "to", required = false) Long toMillis) {
        long to = toMillis == null ? System.currentTimeMillis() : toMillis;
        long from = fromMillis == null ? to - DEFAULT_WINDOW_MILLIS : fromMillis;
        return exporter.export(tenantId, contractReference, purpose, from, to);
    }
}
