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
package org.apache.unomi.router.core.processor;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.file.GenericFile;
import org.apache.unomi.api.services.ExecutionContextManager;
import org.apache.unomi.api.tenants.TenantService;
import org.apache.unomi.router.api.ImportConfiguration;
import org.apache.unomi.router.api.RouterConstants;
import org.apache.unomi.router.api.services.ImportExportConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * A Camel processor that retrieves import configurations based on file names.
 * This processor extracts the configuration ID from the filename and loads
 * the corresponding import configuration for processing.
 *
 * <p>The processor expects filenames in the format:
 * <pre>configurationId.extension</pre>
 * where the configurationId matches an existing import configuration.</p>
 *
 * <p>Features:
 * <ul>
 *   <li>Extracts configuration ID from filename</li>
 *   <li>Loads corresponding import configuration</li>
 *   <li>Sets configuration in exchange header for processing</li>
 *   <li>Handles missing configurations gracefully</li>
 * </ul>
 * </p>
 *
 * @since 1.0
 */
public class ImportConfigByFileNameProcessor implements Processor {

    private static final Logger LOGGER = LoggerFactory.getLogger(ImportConfigByFileNameProcessor.class.getName());

    /** Service for managing import configurations */
    private ImportExportConfigurationService<ImportConfiguration> importConfigurationService;

    private TenantService tenantService;

    private ExecutionContextManager executionContextManager;

    /**
     * Processes the exchange by loading an import configuration based on the filename.
     *
     * <p>This method:
     * <ul>
     *   <li>Extracts the filename from the exchange body</li>
     *   <li>Parses the configuration ID from the filename</li>
     *   <li>Attempts to load the corresponding import configuration</li>
     *   <li>Sets the configuration in the exchange header if found</li>
     *   <li>Stops route processing if no configuration is found</li>
     * </ul>
     * </p>
     *
     * @param exchange the Camel exchange containing the file to process
     * @throws Exception if an error occurs during processing
     */
    @Override
    public void process(Exchange exchange) throws Exception {
        GenericFile<?> file = exchange.getIn().getBody(GenericFile.class);
        String fileName = sanitizeFileName(file.getFileName());
        String filePath = file.getAbsoluteFilePath();

        if (!isValidFilePath(filePath)) {
            LOGGER.warn("Invalid file path detected (possible path traversal attempt): {}", filePath);
            exchange.setProperty(Exchange.ROUTE_STOP, Boolean.TRUE);
            return;
        }

        // Extract tenant ID from the directory path
        String tenantId = extractTenantId(filePath);
        if (tenantId == null || !isValidTenantId(tenantId) || !isValidTenant(tenantId)) {
            LOGGER.warn("Invalid or missing tenant ID in path: {}", filePath);
            exchange.setProperty(Exchange.ROUTE_STOP, Boolean.TRUE);
            return;
        }

        int dotIndex = fileName.indexOf('.');
        if (dotIndex <= 0) {
            LOGGER.warn("Invalid filename format (missing extension): {}", fileName);
            exchange.setProperty(Exchange.ROUTE_STOP, Boolean.TRUE);
            return;
        }
        String importConfigId = fileName.substring(0, dotIndex);

        // Load configuration in tenant context
        ImportConfiguration importConfiguration = executionContextManager.executeAsTenant(tenantId, () ->
            importConfigurationService.load(importConfigId));

        if(importConfiguration != null) {
            LOGGER.debug("Set a header with import configuration found for ID : {} in tenant : {}", importConfigId, tenantId);
            exchange.getIn().setHeader(RouterConstants.HEADER_IMPORT_CONFIG_ONESHOT, importConfiguration);
            exchange.getIn().setHeader(RouterConstants.HEADER_TENANT_ID, tenantId);
        } else {
            LOGGER.warn("No import configuration found with ID : {} in tenant : {}", importConfigId, tenantId);
            exchange.setProperty(Exchange.ROUTE_STOP, Boolean.TRUE);
        }
    }

    /**
     * Validates if the given file path is safe and contains no path traversal attempts.
     *
     * @param filePath the path to validate
     * @return true if the path is safe, false otherwise
     */
    private boolean isValidFilePath(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return false;
        }

        // Normalize path (resolve .. and . segments)
        String normalizedPath = java.nio.file.Paths.get(filePath).normalize().toString();

        // Check if normalization changed the path (indicating potential path traversal)
        if (!filePath.equals(normalizedPath)) {
            return false;
        }

        // Check for path traversal patterns
        return !filePath.contains("../") &&
               !filePath.contains("..\\") &&
               !filePath.contains("%2e%2e%2f") && // URL encoded ../
               !filePath.contains("%2e%2e/") &&   // URL encoded ../ variant
               !filePath.contains("..%2f");       // URL encoded ../ variant
    }

    /**
     * Sanitizes the filename by removing any path components and invalid characters.
     *
     * @param fileName the filename to sanitize
     * @return the sanitized filename
     */
    private String sanitizeFileName(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "";
        }

        // Remove any path components
        fileName = new File(fileName).getName();

        // Remove any non-alphanumeric characters except dots, hyphens, and underscores
        return fileName.replaceAll("[^a-zA-Z0-9._-]", "");
    }

    /**
     * Validates if the given tenant ID contains only valid characters.
     *
     * @param tenantId the tenant ID to validate
     * @return true if the tenant ID is valid, false otherwise
     */
    private boolean isValidTenantId(String tenantId) {
        if (tenantId == null || tenantId.isEmpty()) {
            return false;
        }

        // Only allow alphanumeric characters, hyphens, and underscores in tenant IDs
        return tenantId.matches("^[a-zA-Z0-9_-]+$");
    }

    /**
     * Extracts the tenant ID from the file path.
     * The tenant ID is expected to be the last directory name in the path.
     *
     * @param filePath the absolute path of the file
     * @return the extracted tenant ID or null if not found
     */
    private String extractTenantId(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return null;
        }

        try {
            // Normalize the path first
            String normalizedPath = java.nio.file.Paths.get(filePath).normalize().toString();

            // Split the path and get the parent directory name
            Path path = Paths.get(normalizedPath);
            if (path.getParent() == null) {
                return null;
            }

            String tenantDir = path.getParent().getFileName().toString();

            // Additional safety check for the tenant directory name
            return sanitizeTenantId(tenantDir);
        } catch (Exception e) {
            LOGGER.error("Error extracting tenant ID from path: {}", filePath, e);
            return null;
        }
    }

    /**
     * Sanitizes the tenant ID by removing any invalid characters.
     *
     * @param tenantId the tenant ID to sanitize
     * @return the sanitized tenant ID or null if invalid
     */
    private String sanitizeTenantId(String tenantId) {
        if (tenantId == null || tenantId.isEmpty()) {
            return null;
        }

        // Remove any characters that aren't alphanumeric, hyphen, or underscore
        String sanitized = tenantId.replaceAll("[^a-zA-Z0-9_-]", "");

        // Return null if the sanitization changed the string (indicating it contained invalid chars)
        return tenantId.equals(sanitized) ? sanitized : null;
    }

    /**
     * Validates if the given tenant ID exists.
     *
     * @param tenantId the tenant ID to validate
     * @return true if the tenant exists, false otherwise
     */
    private boolean isValidTenant(String tenantId) {
        return tenantService.getTenant(tenantId) != null;
    }

    /**
     * Sets the service used for managing import configurations.
     *
     * @param importConfigurationService the service for handling import configurations
     */
    public void setImportConfigurationService(ImportExportConfigurationService<ImportConfiguration> importConfigurationService) {
        this.importConfigurationService = importConfigurationService;
    }

    /**
     * Sets the tenant service for the processor.
     *
     * @param tenantService the tenant service to set
     */
    public void setTenantService(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    /**
     * Sets the execution context manager for the processor.
     *
     * @param executionContextManager the execution context manager to set
     */
    public void setExecutionContextManager(ExecutionContextManager executionContextManager) {
        this.executionContextManager = executionContextManager;
    }
}
