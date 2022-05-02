#!/bin/bash
################################################################################
#
#    Licensed to the Apache Software Foundation (ASF) under one or more
#    contributor license agreements.  See the NOTICE file distributed with
#    this work for additional information regarding copyright ownership.
#    The ASF licenses this file to You under the Apache License, Version 2.0
#    (the "License"); you may not use this file except in compliance with
#    the License.  You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#    Unless required by applicable law or agreed to in writing, software
#    distributed under the License is distributed on an "AS IS" BASIS,
#    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#    See the License for the specific language governing permissions and
#    limitations under the License.
#
################################################################################
echo "Copy sources and classes locally"
for project in $(echo ../*/); do
  echo "  get sources for $project"
  if [[ -d ${project}target ]]; then
    echo "    sources and target found for $project"
    cp -rf ${project}src/main src
    cp -rf ${project}target/classes target
    for subproject in $(echo ${project}*/); do
      echo "      get sources for sub $subproject"
      if [[ -d ${subproject}target/classes ]]; then
        echo "        sources and target found for $subproject"
        cp -rf ${subproject}src/main src
        cp -rf ${subproject}target/classes target
      fi
      for subsubproject in $(echo ${subproject}*/); do
        echo "      get sources for sub $subsubproject"
        if [[ -d ${subsubproject}target/classes ]]; then
          echo "        sources and target found for $subsubproject"
          cp -rf ${subsubproject}src/main src
          cp -rf ${subsubproject}target/classes target
        fi
      done
    done
  fi
done
mvn jacoco:report -Dit.code.coverage=true
echo "clean up src/main"
rm -rf src/main
