################################################################################
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
################################################################################

FROM openjdk:8-jre

# Unomi environment variables
ENV UNOMI_HOME /opt/apache-unomi
ENV PATH $PATH:$UNOMI_HOME/bin

ENV KARAF_OPTS "-Dunomi.autoStart=true"

ENV UNOMI_ELASTICSEARCH_ADDRESSES=localhost:9200

ENV UNOMI_VERSION "${project.version}"

WORKDIR $UNOMI_HOME

ADD unomi-${UNOMI_VERSION}.tar.gz ./

RUN mv unomi-*/* . \
	&& rm -rf unomi-*

COPY entrypoint.sh ./entrypoint.sh

EXPOSE 9443
EXPOSE 8181
EXPOSE 8102

CMD ["/bin/bash", "./entrypoint.sh"]
