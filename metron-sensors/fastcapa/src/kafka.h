/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef METRON_KAFKA_H
#define METRON_KAFKA_H

#include <stdio.h>
#include <string.h>
#include <sys/time.h>
#include <endian.h>
#include <librdkafka/rdkafka.h>
#include <rte_common.h>
#include <rte_mbuf.h>

#include "args.h"
#include "types.h"

#ifdef __GNUC__
#  define UNUSED(x) UNUSED_ ## x __attribute__((__unused__))
#else
#  define UNUSED(x) UNUSED_ ## x
#endif

/**
 * Initializes a pool of Kafka connections.
*/
void kaf_init(int num_of_conns);

/**
 * Publish a set of packets to a kafka topic.
 */
int kaf_send(struct rte_mbuf* data[], int num_to_send, int conn_id);

/*
 * Executes polling across all of the kafka client connections.  Ensures that any queued
 * callbacks are served.
 */
void kaf_poll(void);

/**
 * Retrieves a summary of statistics across all of the kafka client connections.
 */
int kaf_stats(struct app_stats *stats);

/**
 * Closes the pool of Kafka connections.
 */
void kaf_close(void);

#endif
