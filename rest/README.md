<!--
  ~ Licensed to the Apache Software Foundation (ASF) under one or more
  ~ contributor license agreements.  See the NOTICE file distributed with
  ~ this work for additional information regarding copyright ownership.
  ~ The ASF licenses this file to You under the Apache License, Version 2.0
  ~ (the "License"); you may not use this file except in compliance with
  ~ the License.  You may obtain a copy of the License at
  ~
  ~      http://www.apache.org/licenses/LICENSE-2.0
  ~
  ~ Unless required by applicable law or agreed to in writing, software
  ~ distributed under the License is distributed on an "AS IS" BASIS,
  ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  ~ See the License for the specific language governing permissions and
  ~ limitations under the License.
  -->

# How to generate the REST API documentation

- Switch to the `rest-documentation` branch, a different branch is need so that we can add the proper `@Path` annotations on the endpoint and add the Maven plugin configuration 
for documentation generation. 
- Make sure that the changes from master are incorporated to the branch by performing a rebase: `git rebase master`.
- Run `mvn test`.
- The documentation should now be available via `target/miredot/index.html`