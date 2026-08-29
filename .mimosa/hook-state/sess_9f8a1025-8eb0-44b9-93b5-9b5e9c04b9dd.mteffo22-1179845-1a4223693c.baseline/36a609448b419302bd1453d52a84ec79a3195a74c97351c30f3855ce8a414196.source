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

import org.apache.unomi.api.Item;
import org.apache.unomi.api.Patch;

import java.net.URL;
import java.util.Enumeration;

/**
 * Service to handle and manage patches on unomi items.
 *
 * The service will load patches from META-INF/cxs/patches in modules. The patch is automatically applied the first time
 * it is viewed by the service, if the item to patch exists, and then saved in persistence.
 *
 * The service also allows to reload a patch from persistence and apply it.
 */
public interface PatchService {
    /**
     * Load a patch from its id
     * @param id the unique id of the patch
     * @return the patch
     */
    Patch load(String id);

    /**
     * Apply a patch
     * @param patch the patch to apply
     * @return the patched item
     */
    Item patch(Patch patch) ;
}

