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
package org.apache.metron.enrichment.bolt;

import com.google.common.cache.LoadingCache;
import org.adrianwalker.multilinestring.Multiline;
import org.apache.metron.common.Constants;
import org.apache.metron.common.error.MetronError;
import org.apache.metron.test.bolt.BaseEnrichmentBoltTest;
import org.apache.metron.test.error.MetronErrorJSONMatcher;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.tuple.Values;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class JoinBoltTest extends BaseEnrichmentBoltTest {

  public class StandAloneJoinBolt extends JoinBolt<JSONObject> {

    public StandAloneJoinBolt(String zookeeperUrl) {
      super(zookeeperUrl);
    }

    @Override
    public void prepare(Map map, TopologyContext topologyContext) {

    }

    @Override
    public Set<String> getStreamIds(JSONObject value) {
      HashSet<String> ret = new HashSet<>();
      for(String s : streamIds) {
        ret.add(s + ":");
      }
      return ret;
    }

    @Override
    public JSONObject joinMessages(Map<String, JSONObject> streamMessageMap) {
      return joinedMessage;
    }
  }

  /**
   {
   "joinField": "joinValue"
   }
   */
  @Multiline
  private String joinedMessageString;

  private JSONObject joinedMessage;

  @Before
  public void parseMessages() {
    JSONParser parser = new JSONParser();
    try {
      joinedMessage = (JSONObject) parser.parse(joinedMessageString);
    } catch (ParseException e) {
      e.printStackTrace();
    }
  }

  @Test
  public void test() throws Exception {
    StandAloneJoinBolt joinBolt = new StandAloneJoinBolt("zookeeperUrl");
    joinBolt.setCuratorFramework(client);
    joinBolt.setTreeCache(cache);
    try {
      joinBolt.prepare(new HashMap(), topologyContext, outputCollector);
      fail("Should fail if a maxCacheSize property is not set");
    } catch(IllegalStateException e) {}
    joinBolt.withMaxCacheSize(100);
    try {
      joinBolt.prepare(new HashMap(), topologyContext, outputCollector);
      fail("Should fail if a maxTimeRetain property is not set");
    } catch(IllegalStateException e) {}
    joinBolt.withMaxTimeRetain(10000);
    joinBolt.prepare(new HashMap(), topologyContext, outputCollector);
    joinBolt.declareOutputFields(declarer);
    verify(declarer, times(1)).declareStream(eq("message"), argThat(new FieldsMatcher("key", "message")));
    when(tuple.getValueByField("key")).thenReturn(key);
    when(tuple.getSourceStreamId()).thenReturn("geo");
    when(tuple.getValueByField("message")).thenReturn(geoMessage);
    joinBolt.execute(tuple);
    verify(outputCollector, times(0)).emit(eq("message"), any(tuple.getClass()), any(Values.class));
    verify(outputCollector, times(0)).ack(tuple);
    when(tuple.getSourceStreamId()).thenReturn("host");
    when(tuple.getValueByField("message")).thenReturn(hostMessage);
    joinBolt.execute(tuple);
    verify(outputCollector, times(0)).emit(eq("message"), any(tuple.getClass()), any(Values.class));
    verify(outputCollector, times(0)).ack(tuple);
    when(tuple.getSourceStreamId()).thenReturn("hbaseEnrichment");
    when(tuple.getValueByField("message")).thenReturn(hbaseEnrichmentMessage);
    joinBolt.execute(tuple);
    when(tuple.getSourceStreamId()).thenReturn("stellar");
    when(tuple.getValueByField("message")).thenReturn(new JSONObject());
    verify(outputCollector, times(0)).emit(eq("message"), any(tuple.getClass()), eq(new Values(key, joinedMessage)));
    joinBolt.execute(tuple);
    verify(outputCollector, times(1)).emit(eq("message"), any(tuple.getClass()), eq(new Values(key, joinedMessage)));
    verify(outputCollector, times(1)).ack(tuple);

    joinBolt.cache = mock(LoadingCache.class);
    when(joinBolt.cache.get(key)).thenThrow(new ExecutionException(new Exception("join exception")));
    joinBolt.execute(tuple);

    MetronError error = new MetronError()
            .withErrorType(Constants.ErrorType.ENRICHMENT_ERROR)
            .withMessage("Joining problem: {}")
            .withThrowable(new ExecutionException(new Exception("join exception")))
            .addRawMessage(new JSONObject());
    verify(outputCollector, times(1)).emit(eq(Constants.ERROR_STREAM), argThat(new MetronErrorJSONMatcher(error.getJSONObject())));
  }
}
