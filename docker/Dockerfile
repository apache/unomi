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
ENV KARAF_INSTALL_PATH /opt
ENV KARAF_HOME $KARAF_INSTALL_PATH/apache-unomi
ENV PATH $PATH:$KARAF_HOME/bin
ENV KARAF_OPTS "-Dunomi.autoStart=true"
WORKDIR $KARAF_HOME

RUN wget http://apache.mirrors.pair.com/incubator/unomi/1.3.0-incubating/unomi-1.3.0-incubating-bin.tar.gz
RUN tar -xzf unomi-1.3.0-incubating-bin.tar.gz
RUN mv unomi-1.3.0-incubating/* .
RUN rm unomi-1.3.0-incubating-bin.tar.gz
RUN rm -r unomi-1.3.0-incubating
COPY ./entrypoint.sh ./entrypoint.sh

EXPOSE 9443
EXPOSE 8181

CMD ["/bin/bash", "./entrypoint.sh"]
