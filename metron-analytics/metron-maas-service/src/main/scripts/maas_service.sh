#!/bin/bash
# 
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
# 
#     http://www.apache.org/licenses/LICENSE-2.0
# 
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
# 

BIGTOP_DEFAULTS_DIR=${BIGTOP_DEFAULTS_DIR-/etc/default}
[ -n "${BIGTOP_DEFAULTS_DIR}" -a -r ${BIGTOP_DEFAULTS_DIR}/hbase ] && . ${BIGTOP_DEFAULTS_DIR}/hbase

# Autodetect JAVA_HOME if not defined
if [ -e /usr/libexec/bigtop-detect-javahome ]; then
  . /usr/libexec/bigtop-detect-javahome
elif [ -e /usr/lib/bigtop-utils/bigtop-detect-javahome ]; then
  . /usr/lib/bigtop-utils/bigtop-detect-javahome
fi

export HBASE_HOME=${HBASE_HOME:-/usr/hdp/current/hbase-client}
export METRON_VERSION=${project.version}
export METRON_HOME=/usr/metron/$METRON_VERSION
export DM_JAR=metron-maas-service-$METRON_VERSION-uber.jar
export HADOOP_OPTS="$HADOOP_OPTS $METRON_JVMFLAGS"
CP=$METRON_HOME/lib/$DM_JAR
HADOOP_CLASSPATH=$(echo $CP )
for jar in $(echo $HADOOP_CLASSPATH | sed 's/:/ /g');do
  if [ -f $jar ];then
    LIBJARS="$jar,$LIBJARS"
  fi
done
export HADOOP_CLASSPATH

yarn jar $METRON_HOME/lib/$DM_JAR org.apache.metron.maas.service.Client "$@"
