#
#  Licensed to the Apache Software Foundation (ASF) under one or more
#  contributor license agreements.  See the NOTICE file distributed with
#  this work for additional information regarding copyright ownership.
#  The ASF licenses this file to You under the Apache License, Version 2.0
#  (the "License"); you may not use this file except in compliance with
#  the License.  You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.
#
import sys
import threading
import signal
import pcapy
import argparse
import random
import logging
import time
import struct
from confluent_kafka import Consumer, KafkaException, KafkaError
from common import to_date, to_hex, unpack_ts


finished = threading.Event()


def signal_handler(signum, frame):
    """ Initiates a clean shutdown for a SIGINT """

    finished.set()
    logging.debug("Clean shutdown process started")


def global_header(args, magic=0xa1b2c3d4L, version_major=2, version_minor=4, zone=0,
                  sigfigs=0, network=1):
    """ Returns the global header used in libpcap-compliant file. """

    return struct.pack("IHHIIII", magic, version_major, version_minor, zone,
        sigfigs, args.snaplen, network)


def packet_header(msg):
    """ Returns the packet header used in a libpcap-compliant file. """

    epoch_micros = struct.unpack_from(">Q", bytes(msg.key()), 0)[0]
    secs = epoch_micros / 1000000
    usec = epoch_micros % 1000000
    caplen = wirelen = len(msg.value())
    hdr = struct.pack('IIII', secs, usec, caplen, wirelen)
    return hdr


def consumer(args, poll_timeout=3.0):
    """ Consumes packets from a Kafka topic. """

    # setup the signal handler
    signal.signal(signal.SIGINT, signal_handler)

    # connect to kafka
    logging.debug("Connecting to Kafka; %s", args.kafka_configs)
    kafka_consumer = Consumer(args.kafka_configs)
    kafka_consumer.subscribe([args.kafka_topic])

    # if 'pretty-print' not set, write libpcap global header
    if args.pretty_print == 0:
        sys.stdout.write(global_header(args))
        sys.stdout.flush()

    try:
        pkts_in = 0
        while not finished.is_set() and (args.max_packets <= 0 or pkts_in < args.max_packets):

            # consume a message from kafka
            msg = kafka_consumer.poll(timeout=poll_timeout)
            if msg is None:
                # no message received
                continue;

            elif msg.error():

                if msg.error().code() == KafkaError._PARTITION_EOF:
                    logging.debug("reached end of topar: topic=%s, partition=%d, offset=%s", msg.topic(), msg.partition(), msg.offset())
                elif msg.error():
                    raise KafkaException(msg.error())

            else:
                pkts_in += 1
                logging.debug("Packet received: pkts_in=%d", pkts_in)

                if args.pretty_print == 0:

                    # write the packet header and packet
                    sys.stdout.write(packet_header(msg))
                    sys.stdout.write(msg.value())
                    sys.stdout.flush()

                elif pkts_in % args.pretty_print == 0:

                    # pretty print
                    print 'Packet: count=%s date=%s topic=%s' % (
                        pkts_in, to_date(unpack_ts(msg.key())), args.kafka_topic)
                    print to_hex(msg.value())

    finally:
        sys.stdout.close()
        kafka_consumer.close()
