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
package org.apache.unomi.samples.login;

import org.osgi.service.component.annotations.Component;

/**
 * Publishes the sample HTML/JS under {@code /login/*} via the OSGi Http Whiteboard
 * (files live in {@code /static} inside this bundle).
 * <p>
 * Static resources only: there is no directory index, so the page is reached at
 * {@code /login/index.html} rather than {@code /login}.
 */
@Component(
        service = Object.class,
        immediate = true,
        property = {
                "osgi.http.whiteboard.resource.pattern=/login/*",
                "osgi.http.whiteboard.resource.prefix=/static"
        }
)
public class LoginSampleResources {
}
