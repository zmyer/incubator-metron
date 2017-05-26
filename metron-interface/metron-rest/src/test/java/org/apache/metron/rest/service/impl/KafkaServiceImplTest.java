/*
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
package org.apache.metron.rest.service.impl;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import kafka.admin.AdminOperationException;
import kafka.admin.AdminUtils$;
import kafka.admin.RackAwareMode;
import kafka.utils.ZkUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.metron.rest.RestException;
import org.apache.metron.rest.model.KafkaTopic;
import org.apache.metron.rest.service.KafkaService;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.springframework.kafka.core.ConsumerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mock;

@SuppressWarnings("unchecked")
@RunWith(PowerMockRunner.class)
@PowerMockIgnore("javax.management.*") // resolve classloader conflict
@PrepareForTest({AdminUtils$.class})
public class KafkaServiceImplTest {
  @Rule
  public final ExpectedException exception = ExpectedException.none();

  private ZkUtils zkUtils;
  private KafkaConsumer<String, String> kafkaConsumer;
  private ConsumerFactory<String, String> kafkaConsumerFactory;
  private AdminUtils$ adminUtils;

  private KafkaService kafkaService;

  private static final KafkaTopic VALID_KAFKA_TOPIC = new KafkaTopic() {{
    setReplicationFactor(2);
    setNumPartitions(1);
    setName("t");
    setProperties(new Properties());
  }};

  @SuppressWarnings("unchecked")
  @Before
  public void setUp() throws Exception {
    zkUtils = mock(ZkUtils.class);
    kafkaConsumerFactory = mock(ConsumerFactory.class);
    kafkaConsumer = mock(KafkaConsumer.class);
    adminUtils = mock(AdminUtils$.class);

    when(kafkaConsumerFactory.createConsumer()).thenReturn(kafkaConsumer);

    kafkaService = new KafkaServiceImpl(zkUtils, kafkaConsumerFactory, adminUtils);
  }

  @Test
  public void listTopicsHappyPathWithListTopicsReturningNull() throws Exception {
    final Map<String, List<PartitionInfo>> topics = null;

    when(kafkaConsumer.listTopics()).thenReturn(topics);

    final Set<String> listedTopics = kafkaService.listTopics();

    assertEquals(Sets.newHashSet(), listedTopics);

    verifyZeroInteractions(zkUtils);
    verify(kafkaConsumer).listTopics();
    verify(kafkaConsumer).close();
    verifyNoMoreInteractions(kafkaConsumer, zkUtils, adminUtils);
  }

  @Test
  public void listTopicsHappyPathWithListTopicsReturningEmptyMap() throws Exception {
    final Map<String, List<PartitionInfo>> topics = new HashMap<>();

    when(kafkaConsumer.listTopics()).thenReturn(topics);

    final Set<String> listedTopics = kafkaService.listTopics();

    assertEquals(Sets.newHashSet(), listedTopics);

    verifyZeroInteractions(zkUtils);
    verify(kafkaConsumer).listTopics();
    verify(kafkaConsumer).close();
    verifyNoMoreInteractions(kafkaConsumer, zkUtils);
  }

  @Test
  public void listTopicsHappyPath() throws Exception {
    final Map<String, List<PartitionInfo>> topics = new HashMap<>();
    topics.put("topic1", Lists.newArrayList());
    topics.put("topic2", Lists.newArrayList());
    topics.put("topic3", Lists.newArrayList());

    when(kafkaConsumer.listTopics()).thenReturn(topics);

    final Set<String> listedTopics = kafkaService.listTopics();

    assertEquals(Sets.newHashSet("topic1", "topic2", "topic3"), listedTopics);

    verifyZeroInteractions(zkUtils);
    verify(kafkaConsumer).listTopics();
    verify(kafkaConsumer).close();
    verifyNoMoreInteractions(kafkaConsumer, zkUtils);
  }

  @Test
  public void listTopicsShouldProperlyRemoveOffsetTopic() throws Exception {
    final Map<String, List<PartitionInfo>> topics = new HashMap<>();
    topics.put("topic1", Lists.newArrayList());
    topics.put("topic2", Lists.newArrayList());
    topics.put("topic3", Lists.newArrayList());
    topics.put("__consumer_offsets", Lists.newArrayList());

    when(kafkaConsumer.listTopics()).thenReturn(topics);

    final Set<String> listedTopics = kafkaService.listTopics();

    assertEquals(Sets.newHashSet("topic1", "topic2", "topic3"), listedTopics);

    verifyZeroInteractions(zkUtils);
    verify(kafkaConsumer).listTopics();
    verify(kafkaConsumer).close();
    verifyNoMoreInteractions(kafkaConsumer, zkUtils);
  }

  @Test
  public void deletingTopicThatDoesNotExistShouldReturnFalse() throws Exception {
    when(kafkaConsumer.listTopics()).thenReturn(Maps.newHashMap());

    assertFalse(kafkaService.deleteTopic("non_existent_topic"));

    verifyZeroInteractions(zkUtils);
    verify(kafkaConsumer).listTopics();
    verify(kafkaConsumer).close();
    verifyNoMoreInteractions(kafkaConsumer, zkUtils);
  }

  @Test
  public void deletingTopicThatExistShouldReturnTrue() throws Exception {
    final Map<String, List<PartitionInfo>> topics = new HashMap<>();
    topics.put("non_existent_topic", Lists.newArrayList());

    when(kafkaConsumer.listTopics()).thenReturn(topics);

    assertTrue(kafkaService.deleteTopic("non_existent_topic"));

    verify(kafkaConsumer).listTopics();
    verify(kafkaConsumer).close();
    verify(adminUtils).deleteTopic(zkUtils, "non_existent_topic");
    verifyNoMoreInteractions(kafkaConsumer);
  }

  @Test
  public void makeSureDeletingTopicReturnsFalseWhenNoTopicsExist() throws Exception {
    final Map<String, List<PartitionInfo>> topics = null;

    when(kafkaConsumer.listTopics()).thenReturn(topics);

    assertFalse(kafkaService.deleteTopic("non_existent_topic"));

    verify(kafkaConsumer).listTopics();
    verify(kafkaConsumer).close();
    verifyNoMoreInteractions(kafkaConsumer);
  }

  @Test
  public void getTopicShouldProperlyMapTopicToKafkaTopic() throws Exception {
    final PartitionInfo partitionInfo = mock(PartitionInfo.class);
    when(partitionInfo.replicas()).thenReturn(new Node[] {new Node(1, "host", 8080)});

    final Map<String, List<PartitionInfo>> topics = new HashMap<>();
    topics.put("t", Lists.newArrayList(partitionInfo));
    topics.put("t1", Lists.newArrayList());

    final KafkaTopic expected = new KafkaTopic();
    expected.setName("t");
    expected.setNumPartitions(1);
    expected.setReplicationFactor(1);

    when(kafkaConsumer.listTopics()).thenReturn(topics);
    when(kafkaConsumer.partitionsFor("t")).thenReturn(Lists.newArrayList(partitionInfo));

    KafkaTopic actual = kafkaService.getTopic("t");
    assertEquals(expected, actual);
    assertEquals(expected.hashCode(), actual.hashCode());
  }

  @Test
  public void getTopicShouldProperlyHandleTopicsThatDontExist() throws Exception {
    final Map<String, List<PartitionInfo>> topics = new HashMap<>();
    topics.put("t1", Lists.newArrayList());

    when(kafkaConsumer.listTopics()).thenReturn(topics);
    when(kafkaConsumer.partitionsFor("t")).thenReturn(Lists.newArrayList());

    assertEquals(null, kafkaService.getTopic("t"));

    verify(kafkaConsumer).listTopics();
    verify(kafkaConsumer, times(0)).partitionsFor("t");
    verify(kafkaConsumer).close();
    verifyZeroInteractions(zkUtils);
    verifyNoMoreInteractions(kafkaConsumer);
  }

  @Test
  public void createTopicShouldFailIfReplicationFactorIsGreaterThanAvailableBrokers() throws Exception {
    final Map<String, List<PartitionInfo>> topics = new HashMap<>();

    when(kafkaConsumer.listTopics()).thenReturn(topics);

    kafkaService.createTopic(VALID_KAFKA_TOPIC);

    verify(adminUtils).createTopic(eq(zkUtils), eq("t"), eq(1), eq(2), eq(new Properties()), eq(RackAwareMode.Disabled$.MODULE$));
    verify(kafkaConsumer).listTopics();
    verifyZeroInteractions(zkUtils);
  }

  @Test
  public void whenAdminUtilsThrowsAdminOperationExceptionCreateTopicShouldProperlyWrapExceptionInRestException() throws Exception {
    exception.expect(RestException.class);

    final Map<String, List<PartitionInfo>> topics = new HashMap<>();
    topics.put("1", new ArrayList<>());

    when(kafkaConsumer.listTopics()).thenReturn(topics);

    doThrow(AdminOperationException.class).when(adminUtils).createTopic(eq(zkUtils), eq("t"), eq(1), eq(2), eq(new Properties()), eq(RackAwareMode.Disabled$.MODULE$));

    kafkaService.createTopic(VALID_KAFKA_TOPIC);
  }

  @Test
  public void getSampleMessageProperlyReturnsAMessageFromAGivenKafkaTopic() throws Exception {
    final String topicName = "t";
    final Node host = new Node(1, "host", 8080);
    final Node[] replicas = {host};
    final List<PartitionInfo> partitionInfo = Lists.newArrayList(new PartitionInfo(topicName, 1, host, replicas, replicas));
    final TopicPartition topicPartition = new TopicPartition(topicName, 1);
    final List<TopicPartition> topicPartitions = Lists.newArrayList(topicPartition);
    final Set<TopicPartition> topicPartitionsSet = Sets.newHashSet(topicPartitions);
    final ConsumerRecords<String, String> records = new ConsumerRecords<>(new HashMap<TopicPartition, List<ConsumerRecord<String, String>>>() {{
      put(topicPartition, Lists.newArrayList(new ConsumerRecord<>(topicName, 1, 1, "k", "message")));
    }});

    when(kafkaConsumer.listTopics()).thenReturn(new HashMap<String, List<PartitionInfo>>() {{ put(topicName, Lists.newArrayList()); }});
    when(kafkaConsumer.partitionsFor(eq(topicName))).thenReturn(partitionInfo);
    when(kafkaConsumer.assignment()).thenReturn(topicPartitionsSet);
    when(kafkaConsumer.position(topicPartition)).thenReturn(1L);
    when(kafkaConsumer.poll(100)).thenReturn(records);

    assertEquals("message", kafkaService.getSampleMessage(topicName));

    verify(kafkaConsumer).assign(eq(topicPartitions));
    verify(kafkaConsumer).assignment();
    verify(kafkaConsumer).poll(100);
    verify(kafkaConsumer).unsubscribe();
    verify(kafkaConsumer, times(2)).position(topicPartition);
    verify(kafkaConsumer).seek(topicPartition, 0);

    verifyZeroInteractions(zkUtils, adminUtils);
  }
}
