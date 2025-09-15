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
package org.apache.unomi.groovy.actions;

import groovy.lang.Script;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Metadata container for compiled Groovy scripts with hash-based change detection.
 * <p>
 * This class encapsulates all metadata associated with a compiled Groovy script,
 * including content hash for efficient change detection and the compiled class
 * for direct execution without recompilation.
 * <p>
 * Thread Safety: This class is immutable and thread-safe. All fields are final
 * and the class provides no methods to modify its state after construction.
 *
 * @since 2.7.0
 */
public final class ScriptMetadata {
    private final String actionName;
    private final String scriptContent;
    private final String contentHash;
    private final long creationTime;
    private final Class<? extends Script> compiledClass;

    /**
     * Constructs a new ScriptMetadata instance.
     *
     * @param actionName    the unique name/identifier of the action
     * @param scriptContent the raw Groovy script content
     * @param compiledClass the compiled Groovy script class
     * @throws IllegalArgumentException if any parameter is null
     */
    public ScriptMetadata(String actionName, String scriptContent, Class<? extends Script> compiledClass) {
        if (actionName == null) {
            throw new IllegalArgumentException("Action name cannot be null");
        }
        if (scriptContent == null) {
            throw new IllegalArgumentException("Script content cannot be null");
        }
        if (compiledClass == null) {
            throw new IllegalArgumentException("Compiled class cannot be null");
        }

        this.actionName = actionName;
        this.scriptContent = scriptContent;
        this.contentHash = calculateHash(scriptContent);
        this.creationTime = System.currentTimeMillis();
        this.compiledClass = compiledClass;
    }

    /**
     * Calculates SHA-256 hash of the given content.
     *
     * @param content the content to hash
     * @return Base64 encoded SHA-256 hash
     * @throws RuntimeException if SHA-256 algorithm is not available
     */
    private String calculateHash(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Determines if the script content has changed compared to new content.
     * <p>
     * This method uses SHA-256 hash comparison for efficient change detection
     * without storing or comparing the full script content.
     *
     * @param newContent the new script content to compare against
     * @return {@code true} if content has changed, {@code false} if unchanged
     * @throws IllegalArgumentException if newContent is null
     */
    public boolean hasChanged(String newContent) {
        if (newContent == null) {
            throw new IllegalArgumentException("New content cannot be null");
        }
        return !contentHash.equals(calculateHash(newContent));
    }

    /**
     * Returns the action name/identifier.
     *
     * @return the action name, never null
     */
    public String getActionName() {
        return actionName;
    }

    /**
     * Returns the original script content.
     *
     * @return the script content, never null
     */
    public String getScriptContent() {
        return scriptContent;
    }

    /**
     * Returns the SHA-256 hash of the script content.
     *
     * @return Base64 encoded content hash, never null
     */
    public String getContentHash() {
        return contentHash;
    }

    /**
     * Returns the timestamp when this metadata was created.
     *
     * @return creation timestamp in milliseconds since epoch
     */
    public long getCreationTime() {
        return creationTime;
    }

    /**
     * Returns the compiled Groovy script class.
     * <p>
     * This class can be used to create new script instances for execution
     * without requiring recompilation.
     *
     * @return the compiled script class, never null
     */
    public Class<? extends Script> getCompiledClass() {
        return compiledClass;
    }
}