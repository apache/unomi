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
package org.apache.unomi.shell.migration;

import groovy.lang.Script;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.osgi.framework.Bundle;
import org.osgi.framework.Version;

import java.io.IOException;
import java.net.URL;

public class MigrateScript implements Comparable<MigrateScript> {


    private final String script;
    private Script compiledScript;
    private final Bundle bundle;
    private final Version version;
    private final int priority;
    private final String name;

    public MigrateScript(URL scriptURL, Bundle bundle) throws IOException {
        this.bundle = bundle;
        this.script = IOUtils.toString(scriptURL);

        // parse file name expected format is: migrate-VERSION-PRIORITY-NAME.groovy like: migrate-1.2.1-00-migrateTags.groovy
        String path = scriptURL.getPath();
        String[] splitName = path.substring(path.lastIndexOf("/"), path.lastIndexOf(".groovy")).split("-");
        this.version = new Version(splitName[1]);
        this.priority = Integer.parseInt(splitName[2]);
        this.name = splitName[3];
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
    public int compareTo(MigrateScript other) {
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

        MigrateScript that = (MigrateScript) o;

        return new EqualsBuilder().append(priority, that.priority).append(version, that.version).append(name, that.name).isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37).append(version).append(priority).append(name).toHashCode();
    }
}
