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

import javax.jws.WebService;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;

/**
 * REST endpoint for managing scheduled tasks in the Apache Unomi system.
 * Provides operations for listing, creating, canceling, and managing tasks.
 */
@WebService
@Produces(MediaType.APPLICATION_JSON)
@CrossOriginResourceSharing(
        allowAllOrigins = true,
        allowCredentials = true
)
@Component(service = TaskEndpoint.class, property = "osgi.jaxrs.resource=true")
@Path("/tasks")
@RequiresRole(UnomiRoles.ADMINISTRATOR)
public class TaskEndpoint {

    @Reference
    private SchedulerService schedulerService;

    /**
     * Retrieves all tasks in the system.
     *
     * @param status optional status filter
     * @param type optional type filter
     * @param offset pagination offset
     * @param limit pagination limit
     * @param sortBy sort field
     * @return a partial list of tasks matching the criteria
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
     * Retrieves a specific task by ID.
     *
     * @param taskId the ID of the task to retrieve
     * @return the requested task
     * @throws WebApplicationException with 404 status if task is not found
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
     * Cancels a scheduled task.
     *
     * @param taskId the ID of the task to cancel
     * @return 204 No Content on success
     * @throws WebApplicationException with 404 status if task is not found
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
     * Retries a failed task.
     *
     * @param taskId the ID of the task to retry
     * @param resetFailureCount whether to reset the failure count
     * @return the retried task
     * @throws WebApplicationException with 404 status if task is not found
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
     * Resumes a crashed task.
     *
     * @param taskId the ID of the task to resume
     * @return the resumed task
     * @throws WebApplicationException with 404 status if task is not found
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
