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
package org.apache.unomi.services.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jsonpatch.JsonPatch;
import com.github.fge.jsonpatch.JsonPatchException;
import org.apache.unomi.api.Item;
import org.apache.unomi.api.Patch;
import org.apache.unomi.api.PropertyType;
import org.apache.unomi.api.services.PatchService;
import org.apache.unomi.persistence.spi.CustomObjectMapper;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.util.Date;
import java.util.Enumeration;

public class PatchServiceImpl implements PatchService {

    private static final Logger logger = LoggerFactory.getLogger(ProfileServiceImpl.class.getName());

    private BundleContext bundleContext;

    private PersistenceService persistenceService;

    public void setBundleContext(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    public void setPersistenceService(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    @Override
    public Patch load(String id) {
        return persistenceService.load(id, Patch.class);
    }

    public void patch(Enumeration<URL> urls, Class<? extends Item> type) {
        if (urls != null) {
            while (urls.hasMoreElements()) {
                patch(urls.nextElement(), type);
            }
        }
    }

    public  void patch(URL patchUrl, Class<? extends Item> type) {
        try {
            Patch patch = CustomObjectMapper.getObjectMapper().readValue(patchUrl, Patch.class);
            if (persistenceService.load(patch.getItemId(), Patch.class) == null) {
                patch(patch, type);
            }
        } catch (IOException e) {
            logger.error("Error while loading patch " + patchUrl, e);
        }
    }

    public <T extends Item> T patch(Patch patch, Class<T> type) {
        if (type == null) {
            throw new IllegalArgumentException("Must specify valid type");
        }

        T item = persistenceService.load(patch.getPatchedItemId(), type);

        if (item != null && patch.getOperation() != null) {
            logger.info("Applying patch " + patch.getItemId());

            switch (patch.getOperation()) {
                case "override":
                    item = CustomObjectMapper.getObjectMapper().convertValue(patch.getData(), type);
                    persistenceService.save(item);
                    break;
                case "patch":
                    JsonNode node = CustomObjectMapper.getObjectMapper().valueToTree(item);
                    JsonPatch jsonPatch = CustomObjectMapper.getObjectMapper().convertValue(patch.getData(), JsonPatch.class);
                    try {
                        JsonNode converted = jsonPatch.apply(node);
                        item = CustomObjectMapper.getObjectMapper().convertValue(converted, type);
                        persistenceService.save(item);
                    } catch (JsonPatchException e) {
                        logger.error("Cannot apply patch",e);
                    }
                    break;
                case "remove":
                    persistenceService.remove(patch.getPatchedItemId(), type);
                    break;
            }

        }

        patch.setLastApplication(new Date());
        persistenceService.save(patch);

        return item;
    }

}
