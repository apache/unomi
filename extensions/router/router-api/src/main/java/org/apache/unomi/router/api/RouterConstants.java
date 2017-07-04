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
package org.apache.unomi.router.api;

/**
 * Created by amidani on 13/06/2017.
 */
public interface RouterConstants {

    String CONFIG_TYPE_NOBROKER = "nobroker";
    String CONFIG_TYPE_KAFKA = "kafka";

    String CONFIG_STATUS_RUNNING = "RUNNING";
    String CONFIG_STATUS_COMPLETE_ERRORS = "ERRORS";
    String CONFIG_STATUS_COMPLETE_SUCCESS = "SUCCESS";
    String CONFIG_STATUS_COMPLETE_WITH_ERRORS = "WITH_ERRORS";

    String IMPORT_EXPORT_CONFIG_TYPE_RECURRENT = "recurrent";
    String IMPORT_EXPORT_CONFIG_TYPE_ONESHOT = "oneshot";

    String DIRECT_IMPORT_DEPOSIT_BUFFER = "direct:depositImportBuffer";
    String DIRECT_EXPORT_DEPOSIT_BUFFER = "direct:depositExportBuffer";

    String DIRECTION_FROM = "from";
    String DIRECTION_TO = "to";

    String HEADER_CONFIG_TYPE = "configType";

    String HEADER_EXPORT_CONFIG = "exportConfig";
    String HEADER_FAILED_MESSAGE = "failedMessage";
    String HEADER_IMPORT_CONFIG_ONESHOT = "importConfigOneShot";

    String IMPORT_ONESHOT_ROUTE_ID = "ONE_SHOT_ROUTE";
    String DEFAULT_FILE_COLUMN_SEPARATOR = ",";

    String DEFAULT_FILE_LINE_SEPARATOR = "\n";
    String KEY_HISTORY_SIZE = "historySize";
    String KEY_CSV_CONTENT = "csvContent";
    String KEY_EXECS = "execs";
    Object KEY_EXECS_DATE = "date";
    Object KEY_EXECS_EXTRACTED = "extractedProfiles";

    String SYSTEM_SCOPE = "integration";
}
