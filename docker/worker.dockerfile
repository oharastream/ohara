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

# download kafka
# WARN: Please don't change the value of KAFKA_DIR
ARG KAFKA_DIR=/opt/kafka
ARG KAFKA_VERSION=2.4.0
ARG SCALA_VERSION=2.12
ARG MIRROR_SITE=https://archive.apache.org/dist
RUN wget $MIRROR_SITE/kafka/${KAFKA_VERSION}/kafka_${SCALA_VERSION}-${KAFKA_VERSION}.tgz
RUN mkdir ${KAFKA_DIR}
RUN tar -zxvf kafka_${SCALA_VERSION}-${KAFKA_VERSION}.tgz -C ${KAFKA_DIR}
RUN rm -f kafka_${SCALA_VERSION}-${KAFKA_VERSION}.tgz
RUN echo "$KAFKA_VERSION" > $(find "${KAFKA_DIR}" -maxdepth 1 -type d -name "kafka_*")/bin/worker_version

# build ohara
ARG BRANCH="master"
ARG COMMIT=$BRANCH
ARG REPO="https://github.com/oharastream/ohara.git"
ARG BEFORE_BUILD=""
WORKDIR /testpatch/ohara
RUN git clone $REPO /testpatch/ohara
RUN git checkout $COMMIT
RUN if [[ "$BEFORE_BUILD" != "" ]]; then /bin/bash -c "$BEFORE_BUILD" ; fi
# we build ohara with specified version of kafka in order to keep the compatibility
RUN ./gradlew clean ohara-connector:build -x test -PskipManager -Pkafka.version=$KAFKA_VERSION -Pscala.version=$SCALA_VERSION
RUN mkdir /opt/ohara
RUN tar -xvf $(find "/testpatch/ohara/ohara-connector/build/distributions" -maxdepth 1 -type f -name "*.tar") -C /opt/ohara/
# copy version file
RUN cp $(find "/opt/ohara/" -maxdepth 1 -type d -name "ohara-*")/bin/ohara_version $(find "${KAFKA_DIR}" -maxdepth 1 -type d -name "kafka_*")/bin/ohara_version
# copy connector jars
RUN cp $(find "/opt/ohara/" -maxdepth 1 -type d -name "ohara-*")/lib/* $(find "${KAFKA_DIR}" -maxdepth 1 -type d -name "kafka_*")/libs/

FROM centos:7.7.1908

# install tools
RUN yum install -y \
  java-1.8.0-openjdk \
  wget # we use wget to download custom plugin from configurator

ENV JAVA_HOME=/usr/lib/jvm/jre

# change user from root to kafka
ARG USER=ohara
RUN groupadd $USER
RUN useradd -ms /bin/bash -g $USER $USER

# copy kafka binary
# TODO: we should remove unused dependencies since this image is used to run broker only
COPY --from=deps /opt/kafka /home/$USER
RUN ln -s $(find "/home/$USER" -maxdepth 1 -type d -name "kafka_*") /home/$USER/default
COPY --from=deps /testpatch/ohara/docker/worker.sh /home/$USER/default/bin/
RUN chmod +x /home/$USER/default/bin/worker.sh
RUN chown -R $USER:$USER /home/$USER
ENV KAFKA_HOME=/home/$USER/default
ENV PATH=$PATH:$KAFKA_HOME/bin

# copy Tini
COPY --from=deps /tini /tini
RUN chmod +x /tini

USER $USER

ENTRYPOINT ["/tini", "--", "worker.sh"]

