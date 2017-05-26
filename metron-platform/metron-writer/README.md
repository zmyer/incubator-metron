<!--
Licensed to the Apache Software Foundation (ASF) under one or more
contributor license agreements.  See the NOTICE file distributed with
this work for additional information regarding copyright ownership.
The ASF licenses this file to You under the Apache License, Version 2.0
(the "License"); you may not use this file except in compliance with
the License.  You may obtain a copy of the License at

	http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 -->

# Writer

## Introduction
The writer module provides some utilties for writing to outside components from within Storm.  This includes managing bulk writing.  An implemention is included for writing to HDFS in this module. Other writers can be found in their own modules.

## HDFS Writer
The HDFS writer included here expands on what Storm has in several ways. There's customization in syncing to HDFS, rotation policy, etc. In addition, the writer allows for users to define output paths based on the fields in the provided JSON message.  This can be defined using Stellar.

To manage the output path, a base path argument is provided by the Flux file, with the FileNameFormat as follows
```
    -   id: "fileNameFormat"
        className: "org.apache.storm.hdfs.bolt.format.DefaultFileNameFormat"
        configMethods:
            -   name: "withPrefix"
                args:
                    - "enrichment-"
            -   name: "withExtension"
                args:
                  - ".json"
            -   name: "withPath"
                args:
                    - "/apps/metron/"
```
This means that all output will land in `/apps/metron/`.  With no further adjustment, it will be `/apps/metron/<sensor>/`.
However, by modifying the sensor's JSON config, it is possible to provide additional pathing based on the the message itself.

E.g.
```
{
  "index": "bro",
  "batchSize": 5,
  "outputPathFunction": "FORMAT('uid-%s', uid)"
}
```
will land data in `/apps/metron/uid-<uid>/`.

For example, if the data contains uid's 1, 3, and 5, there will be 3 output folders in HDFS:
```
/apps/metron/uid-1/
/apps/metron/uid-3/
/apps/metron/uid-5/
```

The Stellar function must return a String, but is not limited to FORMAT functions. Other functions, such as `TO_LOWER`, `TO_UPPER`, etc. are all available for use. Typically, it's preferable to do nontrivial transformations as part of enrichment and simply reference the output here.

If no Stellar function is provided, it will default to putting the sensor in a folder, as above.

A caveat is that the writer will only allow a certain number of files to be created at once.  HdfsWriter has a function `withMaxOpenFiles` allowing this to be set.  The default is 500.  This can be set in Flux:
```
    -   id: "hdfsWriter"
        className: "org.apache.metron.writer.hdfs.HdfsWriter"
        configMethods:
            -   name: "withFileNameFormat"
                args:
                    - ref: "fileNameFormat"
            -   name: "withRotationPolicy"
                args:
                    - ref: "hdfsRotationPolicy"
            -   name: "withMaxOpenFiles"
                args: 500
```

