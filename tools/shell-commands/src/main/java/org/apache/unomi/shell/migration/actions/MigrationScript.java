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
package org.apache.unomi.shell.migration.actions;

import groovy.lang.Script;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.osgi.framework.Bundle;
import org.osgi.framework.Version;

import java.io.IOException;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Java bean representing a migration script, current implementation support groovy script as migration script
 * following file name pattern need to be respected:
 * migrate-VERSION-PRIORITY-NAME.groovy
 *
 * example:
 * migrate-2.0.0-01-segmentReindex.groovy
 */
public class MigrationScript implements Comparable<MigrationScript> {

    private static final Pattern SCRIPT_FILENAME_PATTERN = Pattern.compile("^migrate-(\\d+.\\d+.\\d+)-(\\d+)-([\\w|.]+).groovy$");

    private final String script;
    private Script compiledScript;
    private final Bundle bundle;
    private final Version version;
    private final int priority;
    private final String name;

    public MigrationScript(URL scriptURL, Bundle bundle) throws IOException {
        this.bundle = bundle;
        this.script = IOUtils.toString(scriptURL);

        String path = scriptURL.getPath();
        String fileName = StringUtils.substringAfterLast(path, "/");
        Matcher m = SCRIPT_FILENAME_PATTERN.matcher(fileName);
        if (m.find()) {
            this.version = new Version(m.group(1));
            this.priority = Integer.parseInt(m.group(2));
            this.name = m.group(3);
        } else {
            throw new IllegalStateException("Migration script file name do not respect the expected format: " + fileName +
                    ". Expected format is: migrate-VERSION-PRIORITY-NAME.groovy. Example: migrate-2.0.0-01-segmentReindex.groovy");
        }
    }

    public Script getCompiledScript() {
        return compiledScript;
    }

    public void setCompiledScript(Script compiledScript) {
        this.compiledScript = compiledScript;
    }

    public String getScript() {
        return script;
    }

    public Bundle getBundle() {
        return bundle;
    }

    public Version getVersion() {
        return version;
    }

    public int getPriority() {
        return priority;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return "{" +
                "version=" + version +
                ", name='" + name + '\'' +
                (bundle != null ? ", bundle=" + bundle.getSymbolicName() : "") +
                '}';
    }

    @Override
    public int compareTo(MigrationScript other) {
        int result = version.compareTo(other.getVersion());
        if (result != 0) {
            return result;
        }

        result = priority - other.getPriority();
        if (result != 0) {
            return result;
        }

        return name.compareTo(other.getName());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (o == null || getClass() != o.getClass()) return false;

        MigrationScript that = (MigrationScript) o;

        return new EqualsBuilder().append(priority, that.priority).append(version, that.version).append(name, that.name).isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37).append(version).append(priority).append(name).toHashCode();
    }
}
