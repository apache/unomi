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
package org.apache.unomi.services.impl.patches;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jsonpatch.JsonPatch;
import com.github.fge.jsonpatch.JsonPatchException;
import org.apache.unomi.api.Item;
import org.apache.unomi.api.Patch;
import org.apache.unomi.api.services.PatchService;
import org.apache.unomi.api.services.cache.CacheableTypeConfig;
import org.apache.unomi.persistence.spi.CustomObjectMapper;
import org.apache.unomi.services.common.cache.AbstractMultiTypeCachingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class PatchServiceImpl extends AbstractMultiTypeCachingService implements PatchService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PatchServiceImpl.class.getName());

    public void postConstruct() {
        LOGGER.debug("postConstruct {{}}", bundleContext.getBundle());
        super.postConstruct();
        LOGGER.info("Patch service initialized.");
    }

    public void preDestroy() {
        super.preDestroy();
        LOGGER.info("Patch service shutdown.");
    }

    @Override
    public Patch load(String id) {
        return persistenceService.load(id, Patch.class);
    }

    public Item patch(Patch patch) {
        Class<? extends Item> type = Patch.PATCHABLE_TYPES.get(patch.getPatchedItemType());

        if (type == null) {
            throw new IllegalArgumentException("Must specify valid type");
        }

        Item item = persistenceService.load(patch.getPatchedItemId(), type);

        if (item != null && patch.getOperation() != null) {
            LOGGER.info("Applying patch {}", patch.getItemId());

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
                        LOGGER.error("Cannot apply patch",e);
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

    @Override
    protected Set<CacheableTypeConfig<?>> getTypeConfigs() {
        Set<CacheableTypeConfig<?>> configs = new HashSet<>();
        configs.add(CacheableTypeConfig.builder(Patch.class, Patch.ITEM_TYPE, "patches")
            .withInheritFromSystemTenant(true)
            .withRequiresRefresh(false)
            .withIdExtractor(patch -> patch.getItemId())
            .withUrlComparator((url1, url2) -> url1.getFile().compareTo(url2.getFile()))
            .withPostProcessor(patch -> {
                if (persistenceService.load(patch.getItemId(), Patch.class) == null) {
                    patch(patch);
                }
            })
            .build());
        return configs;
    }

}
