Pycapa
------

* [Overview](#overview)
* [Installation](#installation)
* [Usage](#usage)
  * [Parameters](#parameters)
  * [Examples](#examples)
  * [Kerberos](#kerberos)
* [FAQs](#faqs)

Overview
========

Pycapa performs network packet capture, both off-the-wire and from a Kafka topic, which is useful for the testing and development of [Apache Metron](https://github.com/apache/metron).  It is not intended for production use. The tool will capture packets from a specified interface and push them into a Kafka Topic.  The tool can also do the reverse.  It can consume packets from Kafka and reconstruct each network packet.  This can then be used to create a [libpcap-compliant file](https://wiki.wireshark.org/Development/LibpcapFileFormat) or even to feed directly into a tool like Wireshark to monitor ongoing activity.

Installation
============

General notes on the installation of Pycapa.
* Python 2.7 is required.
* The following package dependencies are required and can be installed automatically with `pip`.
  * [confluent-kafka-python](https://github.com/confluentinc/confluent-kafka-python)
  * [pcapy](https://github.com/CoreSecurity/pcapy)
* These instructions can be used directly on CentOS 7+.  
* Other Linux distributions that come with Python 2.7 can use these instructions with some minor modifications.  
* Older distributions, like CentOS 6, that come with Python 2.6 installed, should install Python 2.7 within a virtual environment and then run Pycapa from within the virtual environment.


1. Install system dependencies including the core development tools, Python libraries and header files, and Libpcap libraries and header files.  On CentOS 7+, you can install these requirements with the following command.

   ```
   yum -y install "@Development tools" python-devel libpcap-devel
   ```

1. Install Librdkafka at your chosen $PREFIX.

   ```
   export PREFIX=/usr

   wget https://github.com/edenhill/librdkafka/archive/v0.9.4.tar.gz  -O - | tar -xz
   cd librdkafka-0.9.4/
   ./configure --prefix=$PREFIX
   make
   make install
   ```

1. Add Librdkafka to the dynamic library load path.

    ```
    echo "$PREFIX/lib" >> /etc/ld.so.conf.d/pycapa.conf
    ldconfig -v
    ```

1. Install Pycapa.  This assumes that you already have the Metron source code on the host.

    ```
    cd metron/metron-sensors/pycapa
    pip install -r requirements.txt
    python setup.py install
    ```

Usage
=====

Pycapa has two primary runtime modes.

* **Producer Mode**: Pycapa can capture packets from a network interface and forward those packets to a Kafka topic.  Pycapa embeds the raw network packet data in the Kafka message body.  The message key contains the timestamp indicating when the packet was captured in microseconds from the epoch, in network byte order.

* **Consumer Mode**: Pycapa can also perform the reverse operation.  It can consume packets from Kafka and reconstruct each network packet.  This can then be used to create a [libpcap-compliant file](https://wiki.wireshark.org/Development/LibpcapFileFormat) or even to feed directly into a tool like Wireshark to monitor activity.

### Parameters

```
$ pycapa -h
usage: pycapa [-h] [-p] [-c] [-k KAFKA_BROKERS] [-t KAFKA_TOPIC]
              [-i NETWORK_IFACE] [-m MAX_PACKETS] [-pp PRETTY_PRINT]
              [-ll LOG_LEVEL] [-X KAFKA_CONFIGS] [-s SNAPLEN]

optional arguments:
  -h, --help            show this help message and exit
  -p, --producer        sniff packets and send to kafka
  -c, --consumer        read packets from kafka
  -k KAFKA_BROKERS, --kafka-broker KAFKA_BROKERS
                        kafka broker(s)
  -t KAFKA_TOPIC, --kafka-topic KAFKA_TOPIC
                        kafka topic
  -i NETWORK_IFACE, --interface NETWORK_IFACE
                        network interface to listen on
  -m MAX_PACKETS, --max-packets MAX_PACKETS
                        stop after this number of packets
  -pp PRETTY_PRINT, --pretty-print PRETTY_PRINT
                        pretty print every X packets
  -ll LOG_LEVEL, --log-level LOG_LEVEL
                        set the log level
  -X KAFKA_CONFIGS      define a kafka client parameter; key=value
  -s SNAPLEN, --snaplen SNAPLEN
                        snapshot length
```

### Examples

**Example**: Capture 10 packets from the `eth0` network interface and forward those to a Kafka topic called `pcap` running on `localhost:9092`.
  ```
  $ pycapa --producer \
      --interface eth0 \
      --kafka-broker localhost:9092 \
      --kafka-topic pcap \
      --max-packets 10
  INFO:root:Connecting to Kafka; {'bootstrap.servers': 'localhost:9092', 'group.id': 'AWBHMIAESAHJ'}
  INFO:root:Starting packet capture
  INFO:root:Waiting for '10' message(s) to flush
  INFO:root:'10' packet(s) in, '10' packet(s) out
  ```

**Example**: Capture packets until SIGINT is received.  A SIGINT is the interrupt signal sent when entering CTRL-D in the console.
  ```
  $ pycapa --producer \
      --interface eth0 \
      --kafka-broker localhost:9092 \
      --kafka-topic pcap
  INFO:root:Connecting to Kafka; {'bootstrap.servers': 'localhost:9092', 'group.id': 'EULLGDOMZDCT'}
  INFO:root:Starting packet capture
  ^C
  INFO:root:Clean shutdown process started
  INFO:root:Waiting for '0' message(s) to flush
  INFO:root:'7' packet(s) in, '7' packet(s) out
  ```

**Example**: While capturing packets, output diagnostic information every 10 packets.
  ```
  $ pycapa --producer \
      --interface en0 \
      --kafka-broker localhost:9092 \
      --kafka-topic pcap \
      --pretty-print 10
  INFO:root:Connecting to Kafka; {'bootstrap.servers': 'localhost:9092', 'group.id': 'YMDSEEDIHVWD'}
  INFO:root:Starting packet capture
  10 packet(s) received
  ac bc 32 bf 0d 43 b8 3e 59 8b 8a 8a 08 00 45 00
  00 3c 00 00 40 00 40 06 b9 66 c0 a8 00 02 c0 a8
  00 03 1f 7c d7 14 5f 8b 82 b4 a8 c5 f6 63 a0 12
  38 90 59 cc 00 00 02 04 05 b4 04 02 08 0a 00 51
  44 17 39 43 3e 9b 01 03 03 04
  20 packet(s) received
  01 00 5e 00 00 fb ac bc 32 bf 0d 43 08 00 45 00
  00 44 d2 09 00 00 ff 11 47 f8 c0 a8 00 03 e0 00
  00 fb 14 e9 14 e9 00 30 69 fc 00 00 00 00 00 01
  00 00 00 00 00 00 0b 5f 67 6f 6f 67 6c 65 63 61
  73 74 04 5f 74 63 70 05 6c 6f 63 61 6c 00 00 0c
  80 01
  ^C
  INFO:root:Clean shutdown process started
  INFO:root:Waiting for '2' message(s) to flush
  INFO:root:'20' packet(s) in, '20' packet(s) out
  ```

**Example**: Consume 10 packets from the Kafka topic `pcap` running on `localhost:9092`, then pipe those into Wireshark for DPI.
  ```
  $ pycapa --consumer \
      --kafka-broker localhost:9092 \
      --kafka-topic pcap \
      --max-packets 10 \
      | tshark -i -
  Capturing on 'Standard input'
      1   0.000000 ArrisGro_0e:65:df → Apple_bf:0d:43 ARP 56 Who has 192.168.0.3? Tell 192.168.0.1
      2   0.000044 Apple_bf:0d:43 → ArrisGro_0e:65:df ARP 42 192.168.0.3 is at ac:bc:32:bf:0d:43
      3   0.203495 fe80::1286:8cff:fe0e:65df → ff02::1      ICMPv6 134 Router Advertisement from 10:86:8c:0e:65:df
      4   2.031988  192.168.0.3 → 96.27.183.249 TCP 54 55110 → 443 [ACK] Seq=1 Ack=1 Win=4108 Len=0
      5   2.035816 192.30.253.125 → 192.168.0.3  TLSv1.2 97 Application Data
      6   2.035892  192.168.0.3 → 192.30.253.125 TCP 66 54671 → 443 [ACK] Seq=1 Ack=32 Win=4095 Len=0 TSval=961120495 TSecr=2658503052
      7   2.035994  192.168.0.3 → 192.30.253.125 TLSv1.2 101 Application Data
      8   2.053866 96.27.183.249 → 192.168.0.3  TCP 66 [TCP ACKed unseen segment] 443 → 55110 [ACK] Seq=1 Ack=2 Win=243 Len=0 TSval=728145145 TSecr=961030381
      9   2.083872 192.30.253.125 → 192.168.0.3  TCP 66 443 → 54671 [ACK] Seq=32 Ack=36 Win=31 Len=0 TSval=2658503087 TSecr=961120495
     10   3.173189 fe80::1286:8cff:fe0e:65df → ff02::1      ICMPv6 134 Router Advertisement from 10:86:8c:0e:65:df
  10 packets captured
  ```

**Example**: Consume 10 packets and create a libpcap-compliant pcap file.
  ```
  $ pycapa --consumer \
      --kafka-broker localhost:9092 \
      --kafka-topic pcap \
      --max-packets 10 \
      > out.pcap
  $ tshark -r out.pcap
      1   0.000000 199.193.204.147 → 192.168.0.3  TLSv1.2 151 Application Data
      2   0.000005 199.193.204.147 → 192.168.0.3  TLSv1.2 1191 Application Data
      3   0.000088  192.168.0.3 → 199.193.204.147 TCP 66 54788 → 443 [ACK] Seq=1 Ack=86 Win=4093 Len=0 TSval=961284465 TSecr=943744612
      4   0.000089  192.168.0.3 → 199.193.204.147 TCP 66 54788 → 443 [ACK] Seq=1 Ack=1211 Win=4058 Len=0 TSval=961284465 TSecr=943744612
      5   0.948788  192.168.0.3 → 192.30.253.125 TCP 54 54671 → 443 [ACK] Seq=1 Ack=1 Win=4096 Len=0
      6   1.005175 192.30.253.125 → 192.168.0.3  TCP 66 [TCP ACKed unseen segment] 443 → 54671 [ACK] Seq=1 Ack=2 Win=31 Len=0 TSval=2658544467 TSecr=961240339
      7   1.636312 fe80::1286:8cff:fe0e:65df → ff02::1      ICMPv6 134 Router Advertisement from 10:86:8c:0e:65:df
      8   2.253052 192.175.27.112 → 192.168.0.3  TLSv1.2 928 Application Data
      9   2.253140  192.168.0.3 → 192.175.27.112 TCP 66 55078 → 443 [ACK] Seq=1 Ack=863 Win=4069 Len=0 TSval=961286699 TSecr=967172238
     10   2.494769  192.168.0.3 → 224.0.0.251  MDNS 82 Standard query 0x0000 PTR _googlecast._tcp.local, "QM" question
  ```

### Kerberos

The probe can be used in a Kerberized environment.  Follow these additional steps to use Pycapa with Kerberos.  The following assumptions have been made.  These may need altered to fit your environment.

  * The Kafka broker is at `kafka1:6667`
  * Zookeeper is at `zookeeper1:2181`
  * The Kafka security protocol is `SASL_PLAINTEXT`
  * The keytab used is located at `/etc/security/keytabs/metron.headless.keytab`
  * The service principal is `metron@EXAMPLE.COM`

1. Build Librdkafka with SASL support (` --enable-sasl`) and install at your chosen $PREFIX.
    ```
    wget https://github.com/edenhill/librdkafka/archive/v0.9.4.tar.gz  -O - | tar -xz
    cd librdkafka-0.9.4/
    ./configure --prefix=$PREFIX --enable-sasl
    make
    make install
    ```

1. Validate Librdkafka does indeed support SASL.  Run the following command and ensure that `sasl` is returned as a built-in feature.
    ```
    $ examples/rdkafka_example -X builtin.features
    builtin.features = gzip,snappy,ssl,sasl,regex
    ```

   If it is not, ensure that you have `libsasl` or `libsasl2` installed.  On CentOS, this can be installed with the following command.
    ```
    yum install -y cyrus-sasl cyrus-sasl-devel cyrus-sasl-gssapi
    ```

1. Grant access to your Kafka topic.  In this example the topic is simply named `pcap`.
    ```
    ${KAFKA_HOME}/bin/kafka-acls.sh \
      --authorizer kafka.security.auth.SimpleAclAuthorizer \
      --authorizer-properties zookeeper.connect=zookeeper1:2181 \
      --add \
      --allow-principal User:metron \
      --topic pcap

    ${KAFKA_HOME}/bin/kafka-acls.sh \
      --authorizer kafka.security.auth.SimpleAclAuthorizer \
      --authorizer-properties zookeeper.connect=zookeeper1:2181 \
      --add \
      --allow-principal User:metron \
      --group pycapa
    ```

1. Use Pycapa as you normally would, but append the following three additional parameters
  * `security.protocol`
  * `sasl.kerberos.keytab`
  * `sasl.kerberos.principal`
  ```
  $ pycapa --producer \
      --interface eth0 \
      --kafka-broker kafka1:6667 \
      --kafka-topic pcap --max-packets 10 \
      -X security.protocol=SASL_PLAINTEXT \
      -X sasl.kerberos.keytab=/etc/security/keytabs/metron.headless.keytab \
      -X sasl.kerberos.principal=metron-metron@METRONEXAMPLE.COM
  INFO:root:Connecting to Kafka; {'sasl.kerberos.principal': 'metron-metron@METRONEXAMPLE.COM', 'group.id': 'ORNLVWJZZUAA', 'security.protocol': 'SASL_PLAINTEXT', 'sasl.kerberos.keytab': '/etc/security/keytabs/metron.headless.keytab', 'bootstrap.servers': 'kafka1:6667'}
  INFO:root:Starting packet capture
  INFO:root:Waiting for '1' message(s) to flush
  INFO:root:'10' packet(s) in, '10' packet(s) out
  ```

FAQs
====

**Question**: How do I get more logs?

Use the following two command-line arguments to get detailed logging.
```
-X debug=all --log-level DEBUG
```
