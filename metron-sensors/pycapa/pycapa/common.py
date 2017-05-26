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
from datetime import datetime
import struct


def to_hex(s):
    """ Transforms a string to hexadecimal notation. """
    hex_str = ' '.join("{0:02x}".format(ord(c)) for c in s)
    return '\n'.join([hex_str[i:i+48] for i in range(0, len(hex_str), 48)])


def to_date(epoch_micros):
    """ Transforms a timestamp in epoch microseconds to a more legible format. """
    epoch_secs = epoch_micros / 1000000.0
    return datetime.fromtimestamp(epoch_secs).strftime('%Y-%m-%d %H:%M:%S.%f')


def pack_ts(ts):
    """ Packs a timestamp into a binary form. """
    return struct.pack(">Q", ts)


def unpack_ts(packed_ts):
    """ Unpacks a timestamp from a binary form. """
    return struct.unpack_from(">Q", bytes(packed_ts), 0)[0]
