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

public interface PatchService {
    Patch load(String id);

    void patch(Enumeration<URL> urls, Class<? extends Item> type);

    void patch(URL patchUrl, Class<? extends Item> type);

    <T extends Item> T patch(Patch patch, Class<T> type) ;
}

