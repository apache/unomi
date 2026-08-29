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
package org.apache.unomi.rest.scheduler;

import org.apache.cxf.rs.security.cors.CrossOriginResourceSharing;
import org.apache.unomi.api.PartialList;
import org.apache.unomi.api.security.UnomiRoles;
import org.apache.unomi.api.services.SchedulerService;
import org.apache.unomi.api.tasks.ScheduledTask;
import org.apache.unomi.rest.security.RequiresRole;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;

/**
 * REST endpoint for managing scheduled tasks in the Apache Unomi system.
 * Provides operations for listing, creating, canceling, and managing tasks.
 */
@Produces(MediaType.APPLICATION_JSON)
@CrossOriginResourceSharing(
        allowAllOrigins = true,
        allowCredentials = true
)
@Component(service = TaskEndpoint.class, property = "osgi.jaxrs.resource=true")
@Path("/tasks")
@RequiresRole({UnomiRoles.ADMINISTRATOR, UnomiRoles.TENANT_ADMINISTRATOR})
public class TaskEndpoint {

    @Reference
    private SchedulerService schedulerService;

    /**
     * Returns scheduled tasks with optional status or type filtering and paging.
     * <p>
     * When neither {@code status} nor {@code type} is set, all tasks are loaded and sliced in memory.
     *
     * @param status optional {@link ScheduledTask.TaskStatus} name (case-insensitive)
     * @param type optional task type filter
     * @param offset zero-based index of the first result
     * @param limit maximum number of results to return
     * @param sortBy optional sort field
     * @return a paged list of matching tasks
     * @api.status 200 org.apache.unomi.api.PartialList ScheduledTask page (list items are ScheduledTask; may be empty).
     * @api.status 400 empty Invalid {@code status} value.
     * @api.example {"list":[{"itemId":"task-1","itemType":"scheduledTask","taskType":"segmentRefresh","status":"SCHEDULED"}],"offset":0,"pageSize":1,"totalSize":1,"totalSizeRelation":"EQUAL"}
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public PartialList<ScheduledTask> getTasks(
            @QueryParam("status") String status,
            @QueryParam("type") String type,
            @QueryParam("offset") @DefaultValue("0") int offset,
            @QueryParam("limit") @DefaultValue("50") int limit,
            @QueryParam("sortBy") String sortBy) {

        if (status != null) {
            try {
                ScheduledTask.TaskStatus taskStatus = ScheduledTask.TaskStatus.valueOf(status.toUpperCase());
                return schedulerService.getTasksByStatus(taskStatus, offset, limit, sortBy);
            } catch (IllegalArgumentException e) {
                throw new WebApplicationException("Invalid status: " + status, Response.Status.BAD_REQUEST);
            }
        } else if (type != null) {
            return schedulerService.getTasksByType(type, offset, limit, sortBy);
        } else {
            List<ScheduledTask> allTasks = schedulerService.getAllTasks();
            int total = allTasks.size();
            int toIndex = Math.min(offset + limit, total);
            if (offset >= total) {
                return new PartialList<ScheduledTask>(allTasks.subList(0, 0), offset, limit, 0, PartialList.Relation.EQUAL);
            }
            return new PartialList<ScheduledTask>(allTasks.subList(offset, toIndex), offset, limit, total, PartialList.Relation.EQUAL);
        }
    }

    /**
     * Returns the scheduled task with the given ID.
     *
     * @param taskId the task identifier
     * @return the requested task
     * @api.status 200 org.apache.unomi.api.tasks.ScheduledTask Task found.
     * @api.status 404 empty Task not found.
     * @api.example {"itemId":"task-1","itemType":"scheduledTask","taskType":"segmentRefresh","status":"SCHEDULED"}
     */
    @GET
    @Path("/{taskId}")
    @Produces(MediaType.APPLICATION_JSON)
    public ScheduledTask getTask(@PathParam("taskId") String taskId) {
        ScheduledTask task = schedulerService.getTask(taskId);
        if (task == null) {
            throw new WebApplicationException("Task not found", Response.Status.NOT_FOUND);
        }
        return task;
    }

    /**
     * Cancels the scheduled task with the given ID.
     *
     * @param taskId the task identifier
     * @return an empty response on success
     * @api.status 204 empty Task cancelled.
     * @api.status 404 empty Task not found.
     * @api.example {}
     */
    @DELETE
    @Path("/{taskId}")
    public Response cancelTask(@PathParam("taskId") String taskId) {
        ScheduledTask task = schedulerService.getTask(taskId);
        if (task == null) {
            throw new WebApplicationException("Task not found", Response.Status.NOT_FOUND);
        }
        schedulerService.cancelTask(taskId);
        return Response.noContent().build();
    }

    /**
     * Retries a failed scheduled task and returns its updated state.
     *
     * @param taskId the task identifier
     * @param resetFailureCount when {@code true}, resets the failure counter before retry
     * @return the retried task
     * @api.status 200 org.apache.unomi.api.tasks.ScheduledTask Task retried.
     * @api.status 404 empty Task not found.
     * @api.example {"itemId":"task-1","itemType":"scheduledTask","taskType":"segmentRefresh","status":"SCHEDULED"}
     */
    @POST
    @Path("/{taskId}/retry")
    @Produces(MediaType.APPLICATION_JSON)
    public ScheduledTask retryTask(
            @PathParam("taskId") String taskId,
            @QueryParam("resetFailureCount") @DefaultValue("false") boolean resetFailureCount) {
        ScheduledTask task = schedulerService.getTask(taskId);
        if (task == null) {
            throw new WebApplicationException("Task not found", Response.Status.NOT_FOUND);
        }
        schedulerService.retryTask(taskId, resetFailureCount);
        return schedulerService.getTask(taskId);
    }

    /**
     * Resumes a crashed scheduled task and returns its updated state.
     *
     * @param taskId the task identifier
     * @return the resumed task
     * @api.status 200 org.apache.unomi.api.tasks.ScheduledTask Task resumed.
     * @api.status 404 empty Task not found.
     * @api.example {"itemId":"task-1","itemType":"scheduledTask","taskType":"segmentRefresh","status":"RUNNING"}
     */
    @POST
    @Path("/{taskId}/resume")
    @Produces(MediaType.APPLICATION_JSON)
    public ScheduledTask resumeTask(@PathParam("taskId") String taskId) {
        ScheduledTask task = schedulerService.getTask(taskId);
        if (task == null) {
            throw new WebApplicationException("Task not found", Response.Status.NOT_FOUND);
        }
        schedulerService.resumeTask(taskId);
        return schedulerService.getTask(taskId);
    }
}
