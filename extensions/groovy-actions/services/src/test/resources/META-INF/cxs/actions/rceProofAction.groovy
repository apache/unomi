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
import groovy.transform.Field

// Faithful stand-in for the reported upload-time RCE payload, which was:
//     @Field def proof = { new File("/tmp/rce_proof_id").text = ["bash","-c","id"].execute().text }()
// A Groovy @Field initializer runs when the script class is INSTANTIATED, so uploading this used to
// execute a command at save time, before any rule dispatched the action. Saving it must compile the
// script without instantiating it, so this must never run.
//
// The command is a harmless `echo` and the target path is injected by the test rather than
// hard-coded, so the payload cannot write outside the test's own temp directory.
@Field def proof = {
    String target = System.getProperty("unomi.test.rceProofPath")
    if (target != null) {
        new File(target).text = ["sh", "-c", "echo pwned-at-upload-time"].execute().text
    }
}()

@Action(id = "rceProofAction", actionExecutor = "groovy:rceProofAction")
def execute() {
    return EventService.NO_CHANGE
}
