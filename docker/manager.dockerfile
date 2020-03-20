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
WORKDIR /testpatch/ohara
RUN git clone $REPO /testpatch/ohara
RUN git checkout $COMMIT
RUN if [[ "$BEFORE_BUILD" != "" ]]; then /bin/bash -c "$BEFORE_BUILD" ; fi
RUN ./gradlew clean ohara-manager:build -x test
RUN mkdir /opt/ohara
RUN tar -xvf $(find "/testpatch/ohara/ohara-manager/build/distributions" -maxdepth 1 -type f -name "*.tar") -C /opt/ohara/

FROM centos:7.7.1908

# install nodejs
# NOTED: ohara-manager requires nodejs 10.x
RUN curl -sL https://rpm.nodesource.com/setup_10.x | bash -
RUN yum install -y nodejs

# install yarn
RUN npm install -g yarn@1.17.3

# add user
ARG USER=ohara
RUN groupadd $USER
RUN useradd -ms /bin/bash -g $USER $USER

# clone ohara binary
COPY --from=deps /opt/ohara /home/$USER
RUN ln -s $(find "/home/$USER/" -maxdepth 1 -type d -name "ohara-*") /home/$USER/default
COPY --from=deps /testpatch/ohara/docker/manager.sh /home/$USER/default/bin/
# There are too many files in ohara-manager and the chmod is slow in docker (see https://github.com/docker/for-linux/issues/388)
# Hence, we keep the root owner for those files.
# RUN chown -R $USER:$USER /home/$USER
RUN chmod +x /home/$USER/default/bin/manager.sh
ENV OHARA_HOME=/home/$USER/default
ENV PATH=$PATH:$OHARA_HOME/bin

# clone Tini
COPY --from=deps /tini /tini
RUN chmod +x /tini

# change to user
USER $USER

ENTRYPOINT ["/tini", "--", "manager.sh"]