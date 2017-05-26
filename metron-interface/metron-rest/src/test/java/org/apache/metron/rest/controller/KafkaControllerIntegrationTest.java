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
package org.apache.metron.rest.controller;

import kafka.common.TopicAlreadyMarkedForDeletionException;
import org.adrianwalker.multilinestring.Multiline;
import org.apache.metron.integration.components.KafkaComponent;
import org.apache.metron.rest.generator.SampleDataGenerator;
import org.apache.metron.rest.service.KafkaService;
import org.hamcrest.Matchers;
import org.json.simple.parser.ParseException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.util.NestedServletException;

import java.io.IOException;

import static org.apache.metron.rest.MetronRestConstants.TEST_PROFILE;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment= SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles(TEST_PROFILE)
public class KafkaControllerIntegrationTest {

  private static final int KAFKA_RETRY = 10;
  @Autowired
  private KafkaComponent kafkaWithZKComponent;

  class SampleDataRunner implements Runnable {

    private boolean stop = false;
    private String path = "../../metron-platform/metron-integration-test/src/main/sample/data/bro/raw/BroExampleOutput";

    @Override
    public void run() {
      SampleDataGenerator broSampleDataGenerator = new SampleDataGenerator();
      broSampleDataGenerator.setBrokerUrl(kafkaWithZKComponent.getBrokerList());
      broSampleDataGenerator.setNum(1);
      broSampleDataGenerator.setSelectedSensorType("bro");
      broSampleDataGenerator.setDelay(0);
      try {
        while(!stop) {
          broSampleDataGenerator.generateSampleData(path);
        }
      } catch (ParseException|IOException e) {
        e.printStackTrace();
      }
    }

    public void stop() {
      stop = true;
    }
  }

  private SampleDataRunner sampleDataRunner =  new SampleDataRunner();
  private Thread sampleDataThread = new Thread(sampleDataRunner);

  /**
   {
   "name": "bro",
   "numPartitions": 1,
   "properties": {},
   "replicationFactor": 1
   }
   */
  @Multiline
  public static String broTopic;

  @Autowired
  private WebApplicationContext wac;

  @Autowired
  private KafkaService kafkaService;

  private MockMvc mockMvc;

  private String kafkaUrl = "/api/v1/kafka";
  private String user = "user";
  private String password = "password";

  @Before
  public void setup() throws Exception {
    this.mockMvc = MockMvcBuilders.webAppContextSetup(this.wac).apply(springSecurity()).build();
  }

  @Test
  public void testSecurity() throws Exception {
    this.mockMvc.perform(post(kafkaUrl + "/topic").with(csrf()).contentType(MediaType.parseMediaType("application/json;charset=UTF-8")).content(broTopic))
            .andExpect(status().isUnauthorized());

    this.mockMvc.perform(get(kafkaUrl + "/topic/bro"))
            .andExpect(status().isUnauthorized());

    this.mockMvc.perform(get(kafkaUrl + "/topic"))
            .andExpect(status().isUnauthorized());

    this.mockMvc.perform(get(kafkaUrl + "/topic/bro/sample"))
            .andExpect(status().isUnauthorized());

    this.mockMvc.perform(delete(kafkaUrl + "/topic/bro").with(csrf()))
            .andExpect(status().isUnauthorized());
  }

  @Test
  public void test() throws Exception {
    this.kafkaService.deleteTopic("bro");
    this.kafkaService.deleteTopic("someTopic");
    Thread.sleep(1000);

    this.mockMvc.perform(delete(kafkaUrl + "/topic/bro").with(httpBasic(user,password)).with(csrf()))
            .andExpect(status().isNotFound());

    this.mockMvc.perform(post(kafkaUrl + "/topic").with(httpBasic(user,password)).with(csrf()).contentType(MediaType.parseMediaType("application/json;charset=UTF-8")).content(broTopic))
            .andExpect(status().isCreated())
            .andExpect(content().contentType(MediaType.parseMediaType("application/json;charset=UTF-8")))
            .andExpect(jsonPath("$.name").value("bro"))
            .andExpect(jsonPath("$.numPartitions").value(1))
            .andExpect(jsonPath("$.replicationFactor").value(1));

    sampleDataThread.start();
    Thread.sleep(1000);

    this.mockMvc.perform(get(kafkaUrl + "/topic/bro").with(httpBasic(user,password)))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.parseMediaType("application/json;charset=UTF-8")))
            .andExpect(jsonPath("$.name").value("bro"))
            .andExpect(jsonPath("$.numPartitions").value(1))
            .andExpect(jsonPath("$.replicationFactor").value(1));

    this.mockMvc.perform(get(kafkaUrl + "/topic/someTopic").with(httpBasic(user,password)))
            .andExpect(status().isNotFound());

    this.mockMvc.perform(get(kafkaUrl + "/topic").with(httpBasic(user,password)))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.parseMediaType("application/json;charset=UTF-8")))
            .andExpect(jsonPath("$", Matchers.hasItem("bro")));
    for(int i = 0;i < KAFKA_RETRY;++i) {
      MvcResult result = this.mockMvc.perform(get(kafkaUrl + "/topic/bro/sample").with(httpBasic(user, password)))
              .andReturn();
      if(result.getResponse().getStatus() == 200) {
        break;
      }
      Thread.sleep(1000);
    }
    this.mockMvc.perform(get(kafkaUrl + "/topic/bro/sample").with(httpBasic(user,password)))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.parseMediaType("text/plain;charset=UTF-8")))
            .andExpect(jsonPath("$").isNotEmpty());

    this.mockMvc.perform(get(kafkaUrl + "/topic/someTopic/sample").with(httpBasic(user,password)))
            .andExpect(status().isNotFound());
    boolean deleted = false;
    for(int i = 0;i < KAFKA_RETRY;++i) {
      try {
        MvcResult result = this.mockMvc.perform(delete(kafkaUrl + "/topic/bro").with(httpBasic(user, password)).with(csrf())).andReturn();
        if(result.getResponse().getStatus() == 200) {
          deleted = true;
          break;
        }
        Thread.sleep(1000);
      }
      catch(NestedServletException nse) {
        Throwable t = nse.getRootCause();
        if(t instanceof TopicAlreadyMarkedForDeletionException) {
          continue;
        }
        else {
          throw nse;
        }
      }
      catch(Throwable t) {
        throw t;
      }
    }
    if(!deleted) {
      throw new IllegalStateException("Unable to delete kafka topic \"bro\"");
    }
  }

  @After
  public void tearDown() {
    sampleDataRunner.stop();
  }
}
