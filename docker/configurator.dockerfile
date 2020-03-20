#
# Copyright 2019 is-land
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

FROM oharastream/ohara:deps as deps

# add label to intermediate image so jenkins can find out this one to remove
ARG STAGE="intermediate"
LABEL stage=$STAGE

ARG BRANCH="master"
ARG COMMIT=$BRANCH
ARG REPO="https://github.com/oharastream/ohara.git"
ARG BEFORE_BUILD=""
ARG KAFKA_VERSION=2.4.0
ARG SCALA_VERSION=2.12
# this argument is used to change default version of services
ARG OHARA_VERSION=""
WORKDIR /testpatch/ohara
RUN git clone $REPO /testpatch/ohara
RUN git checkout $COMMIT
RUN if [[ "$BEFORE_BUILD" != "" ]]; then /bin/bash -c "$BEFORE_BUILD" ; fi
RUN ./gradlew clean ohara-configurator:build -x test \
  -PskipManager \
  -Pkafka.version=$KAFKA_VERSION \
  -Pscala.version=$SCALA_VERSION \
  -Pohara.version=$OHARA_VERSION
RUN mkdir /opt/ohara
RUN tar -xvf $(find "/testpatch/ohara/ohara-configurator/build/distributions" -maxdepth 1 -type f -name "*.tar") -C /opt/ohara/

FROM centos:7.7.1908

# install tools
RUN yum install -y \
  java-1.8.0-openjdk

# export JAVA_HOME
ENV JAVA_HOME=/usr/lib/jvm/jre

# add user
ARG USER=ohara
RUN groupadd $USER
RUN useradd -ms /bin/bash -g $USER $USER

# clone ohara binary
COPY --from=deps /opt/ohara /home/$USER
RUN ln -s $(find "/home/$USER/" -maxdepth 1 -type d -name "ohara-*") /home/$USER/default
COPY --from=deps /testpatch/ohara/docker/configurator.sh /home/$USER/default/bin/
RUN chown -R $USER:$USER /home/$USER
RUN chmod +x /home/$USER/default/bin/configurator.sh
ENV OHARA_HOME=/home/$USER/default
ENV PATH=$PATH:$OHARA_HOME/bin

# clone Tini
COPY --from=deps /tini /tini
RUN chmod +x /tini

# change to user
USER $USER

ENTRYPOINT ["/tini", "--", "configurator.sh"]
