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

package org.apache.unomi.didvc.services.impl;

import org.apache.unomi.api.Event;
import org.apache.unomi.api.services.EventService;
import org.apache.unomi.didvc.api.DidvcEventTypes;
import org.apache.unomi.didvc.api.items.CredentialRecord;
import org.apache.unomi.didvc.api.services.CredentialRefreshService;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Credential re-verification lifecycle: annual-refresh windows and
 * identity-change triggers (e.g. SIM re-registration), swept on a schedule.
 * Production deployments may swap the local scheduler for Unomi's
 * cluster-aware SchedulerService; the decision logic is identical.
 */
@Component(service = CredentialRefreshService.class, immediate = true)
public class CredentialRefreshServiceImpl implements CredentialRefreshService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CredentialRefreshServiceImpl.class);
    private static final long SWEEP_INTERVAL_HOURS = 6;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    @Reference
    private PersistenceService persistenceService;
    @Reference(cardinality = ReferenceCardinality.OPTIONAL)
    private EventService eventService;

    public void setPersistenceService(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    public void setEventService(EventService eventService) {
        this.eventService = eventService;
    }

    @Activate
    public void activate() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                int marked = sweepExpiringCredentials(new Date());
                if (marked > 0) {
                    LOGGER.info("Marked {} credentials refresh-due", marked);
                }
            } catch (Exception e) {
                LOGGER.warn("Credential refresh sweep failed", e);
            }
        }, SWEEP_INTERVAL_HOURS, SWEEP_INTERVAL_HOURS, TimeUnit.HOURS);
    }

    @Deactivate
    public void deactivate() {
        scheduler.shutdownNow();
    }

    @Override
    public boolean isRefreshDue(CredentialRecord record, Date now) {
        if (record.isRefreshDue() || record.isRevoked()) {
            return true;
        }
        return record.getExpiresAt() != null
                && now.getTime() >= record.getExpiresAt().getTime() - DEFAULT_REFRESH_WINDOW_MILLIS;
    }

    @Override
    public int markRefreshDueForSubject(String subjectId) {
        int marked = 0;
        for (CredentialRecord record : persistenceService.getAllItems(CredentialRecord.class)) {
            if (subjectId.equals(record.getSubjectId()) && !record.isRefreshDue()) {
                record.setRefreshDue(true);
                persistenceService.save(record);
                emitRefreshEvent(record, "identityChanged");
                marked++;
            }
        }
        return marked;
    }

    @Override
    public int sweepExpiringCredentials(Date now) {
        int marked = 0;
        for (CredentialRecord record : persistenceService.getAllItems(CredentialRecord.class)) {
            if (record.getExpiresAt() != null && !record.isRefreshDue()
                    && now.getTime() >= record.getExpiresAt().getTime() - DEFAULT_REFRESH_WINDOW_MILLIS) {
                record.setRefreshDue(true);
                persistenceService.save(record);
                emitRefreshEvent(record, "expiryWindow");
                marked++;
            }
        }
        return marked;
    }

    private void emitRefreshEvent(CredentialRecord record, String reason) {
        if (eventService == null) {
            return;
        }
        try {
            Map<String, Object> properties = new HashMap<>();
            properties.put("recordId", record.getItemId());
            properties.put("schemaId", record.getSchemaId());
            properties.put("subjectId", record.getSubjectId());
            properties.put("reason", reason);
            Event event = new Event(DidvcEventTypes.DIDVC_ISSUED, null, null, "didvc",
                    null, null, properties, new Date(), true);
            event.setTenantId(record.getTenantId());
            eventService.send(event);
        } catch (Exception e) {
            LOGGER.warn("Failed to emit refresh event for {}", record.getItemId(), e);
        }
    }
}
