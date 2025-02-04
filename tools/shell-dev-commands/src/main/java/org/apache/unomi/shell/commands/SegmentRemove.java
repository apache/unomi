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
package org.apache.unomi.shell.commands;

import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.unomi.api.segments.DependentMetadata;
import org.apache.unomi.api.services.SegmentService;

@Command(scope = "unomi", name = "segment-remove", description = "Remove segments in the Apache Unomi Context Server")
@Service
public class SegmentRemove extends RemoveCommandSupport {

    @Reference
    SegmentService segmentService;

    @Argument(index = 0, name = "segmentId", description = "The identifier for the segment", required = true, multiValued = false)
    String segmentIdentifier;

    @Argument(index = 1, name = "validate", description = "Check if the segment is used in goals or other segments", required = false, multiValued = false)
    Boolean validate = true;

    @Override
    public Object doRemove() throws Exception {
        DependentMetadata dependantMetadata = segmentService.removeSegmentDefinition(segmentIdentifier, validate);
        if (!validate || (dependantMetadata.getSegments().isEmpty() && dependantMetadata.getScorings().isEmpty())) {
            System.out.println("Segment " + segmentIdentifier + " successfully deleted");
        } else if (validate) {
            System.out.print("Segment " + segmentIdentifier + " could not be deleted because of the following dependents:");
            if (!dependantMetadata.getScorings().isEmpty()) {
                System.out.print(" scoring:" + dependantMetadata.getScorings());
            }
            if (!dependantMetadata.getSegments().isEmpty()) {
                System.out.println(" segments:" + dependantMetadata.getSegments());
            }
        }
        return null;
    }

    @Override
    public String getResourceDescription() {
        return "segment [" + segmentIdentifier + "]";
    }

}
