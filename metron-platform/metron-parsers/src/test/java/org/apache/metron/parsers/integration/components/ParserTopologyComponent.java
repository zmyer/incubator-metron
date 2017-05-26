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
package org.apache.metron.parsers.integration.components;

import com.google.common.collect.ImmutableMap;
import org.apache.storm.Config;
import org.apache.storm.LocalCluster;
import org.apache.storm.topology.TopologyBuilder;
import org.apache.metron.integration.InMemoryComponent;
import org.apache.metron.integration.UnableToStartException;
import org.apache.metron.parsers.topology.ParserTopologyBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static org.apache.metron.integration.components.FluxTopologyComponent.assassinateSlots;
import static org.apache.metron.integration.components.FluxTopologyComponent.cleanupWorkerDir;

public class ParserTopologyComponent implements InMemoryComponent {

  protected static final Logger LOG = LoggerFactory.getLogger(ParserTopologyComponent.class);
  private Properties topologyProperties;
  private String brokerUrl;
  private String sensorType;
  private LocalCluster stormCluster;
  private String outputTopic;

  public static class Builder {
    Properties topologyProperties;
    String brokerUrl;
    String sensorType;
    String outputTopic;
    public Builder withTopologyProperties(Properties topologyProperties) {
      this.topologyProperties = topologyProperties;
      return this;
    }
    public Builder withBrokerUrl(String brokerUrl) {
      this.brokerUrl = brokerUrl;
      return this;
    }
    public Builder withSensorType(String sensorType) {
      this.sensorType = sensorType;
      return this;
    }

    public Builder withOutputTopic(String topic) {
      this.outputTopic = topic;
      return this;
    }

    public ParserTopologyComponent build() {
      return new ParserTopologyComponent(topologyProperties, brokerUrl, sensorType, outputTopic);
    }
  }

  public ParserTopologyComponent(Properties topologyProperties, String brokerUrl, String sensorType, String outputTopic) {
    this.topologyProperties = topologyProperties;
    this.brokerUrl = brokerUrl;
    this.sensorType = sensorType;
    this.outputTopic = outputTopic;
  }


  @Override
  public void start() throws UnableToStartException {
    try {
      TopologyBuilder topologyBuilder = ParserTopologyBuilder.build(topologyProperties.getProperty("kafka.zk")
                                                                   , Optional.ofNullable(brokerUrl)
                                                                   , sensorType
                                                                   , 1
                                                                   , 1
                                                                   , 1
                                                                   , 1
                                                                   , 1
                                                                   , 1
                                                                   , null
                                                                   , Optional.empty()
                                                                   , Optional.ofNullable(outputTopic)
                                                                   );
      Map<String, Object> stormConf = new HashMap<>();
      stormConf.put(Config.TOPOLOGY_DEBUG, true);
      stormCluster = new LocalCluster();
      stormCluster.submitTopology(sensorType, stormConf, topologyBuilder.createTopology());
    } catch (Exception e) {
      throw new UnableToStartException("Unable to start parser topology for sensorType: " + sensorType, e);
    }
  }

  @Override
  public void stop() {
    if (stormCluster != null) {
      try {
        try {
          stormCluster.shutdown();
        } catch (IllegalStateException ise) {
          if (!(ise.getMessage().contains("It took over") && ise.getMessage().contains("to shut down slot"))) {
            throw ise;
          }
          else {
            assassinateSlots();
            LOG.error("Storm slots didn't shut down entirely cleanly *sigh*.  " +
                    "I gave them the old one-two-skadoo and killed the slots with prejudice.  " +
                    "If tests fail, we'll have to find a better way of killing them.", ise);
          }
        }
      }
      catch(Throwable t) {
        LOG.error(t.getMessage(), t);
      }
      finally {
        cleanupWorkerDir();
      }

    }
  }
}
