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

if [[ -z "$KAFKA_HOME" ]];then
  echo "$KAFKA_HOME is required!!!"
  exit 2
fi

VERSION_FILE=$KAFKA_HOME/bin/broker_version
OHARA_VERSION_FILE=$KAFKA_HOME/bin/ohara_version
CONFIG_FILE=$KAFKA_HOME/config/broker.config

# parsing arguments
while [[ $# -gt 0 ]]; do
  key="$1"

  case $key in
    -v|-version)
    if [[ -f "$VERSION_FILE" ]]; then
      echo "broker $(cat "$VERSION_FILE")"
    else
      echo "broker: unknown"
    fi
    if [[ -f "$OHARA_VERSION_FILE" ]]; then
      echo "ohara $(cat "$OHARA_VERSION_FILE")"
    else
      echo "ohara: unknown"
    fi
    java -version
    exit
    ;;
    --config)
    CONFIG_FILE="$2"
    shift
    shift
    ;;
    -f|--file)
    FILE_DATA+=("$2")
    shift
    shift
    ;;
    *)
    echo "unknown key:$key"
    exit 1
    ;;
  esac
done

# parsing files
## we will loop all the files in FILE_DATA of arguments : --file A --file B --file C
## the format of A, B, C should be file_path=k1=v1,k2=v2,k3,k4=v4...
for f in "${FILE_DATA[@]}"; do
  key=${f%%=*}
  value=${f#*=}
  dir=$(dirname "$key")

  [ -d $dir ] || mkdir -p $dir

  IFS=',' read -ra VALUES <<< "$value"
  for i in "${VALUES[@]}"; do
    echo "-- save line : \"$i\" to $key"
    echo "$i" >> $key
  done
done

if [[ ! -f "$CONFIG_FILE" ]]; then
  echo "$CONFIG_FILE is required!!!"
  exit 2
fi

exec $KAFKA_HOME/bin/kafka-server-start.sh "$CONFIG_FILE"
