#!/usr/bin/env bash
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


#----------[LOCATE PROJECT]----------#
SOURCE="${BASH_SOURCE[0]}"
BIN_DIR="$( dirname "$SOURCE" )"
while [ -h "$SOURCE" ]
do
  SOURCE="$(readlink "$SOURCE")"
  BIN_DIR="$( cd -P "$( dirname "$SOURCE"  )" && pwd )"
done
BIN_DIR="$( cd -P "$( dirname "$SOURCE" )" && pwd )"

SCRIPT=""
if [ "$1" == "start" ]; then
  SCRIPT="$BIN_DIR/start-service.sh"
  shift 1
elif [ "$1" == "stop" ]; then
  SCRIPT="$BIN_DIR/stop-service.sh"
  shift 1
elif [ "$1" == "-v" ] || [ "$1" == "-version" ]; then
  # ohara-manager is NOT java project so it has a version file instead of running VersionUtils.
  if [ -f "$BIN_DIR/ohara_version" ]; then
    cat "$BIN_DIR/ohara_version"
  else
    "$BIN_DIR/run_java.sh" oharastream.ohara.common.util.VersionUtils
    java -version
  fi
  exit 0
else
  echo "Usage: (start|stop) {service name}"
  exit 1
fi

ARGS=""
i=0
while [ -n "$1" ]
do
  ARGS=$ARGS" "$1
  i=$(($i+1))
  shift
done

# load envs
. "$BIN_DIR/ohara-env.sh"
echo "script: $SCRIPT"
echo "command-line arguments: $ARGS"
exec $SCRIPT $ARGS
