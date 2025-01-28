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
package org.apache.unomi.shell.dev.completers;

import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.karaf.shell.api.console.CommandLine;
import org.apache.karaf.shell.api.console.Completer;
import org.apache.karaf.shell.api.console.Session;
import org.apache.karaf.shell.support.completers.StringsCompleter;
import org.apache.unomi.api.Metadata;
import org.apache.unomi.api.PartialList;
import org.apache.unomi.api.services.SegmentService;
import org.osgi.service.component.annotations.Reference;

@Service
public class SegmentCompleter implements Completer {

    private static final int DEFAULT_LIMIT = 50;

    private SegmentService segmentService;

    @Reference
    public void setSegmentService(SegmentService segmentService) {
        this.segmentService = segmentService;
    }

    @Override
    public int complete(Session session, CommandLine commandLine, java.util.List<String> candidates) {
        StringsCompleter delegate = new StringsCompleter();

        try {
            // Get segments sorted by name
            PartialList<Metadata> segments = segmentService.getSegmentMetadatas(0, DEFAULT_LIMIT, "name:asc");
            for (Metadata segment : segments.getList()) {
                delegate.getStrings().add(segment.getId());
            }
        } catch (Exception e) {
            // Log error or handle exception
        }

        return delegate.complete(session, commandLine, candidates);
    }
}
