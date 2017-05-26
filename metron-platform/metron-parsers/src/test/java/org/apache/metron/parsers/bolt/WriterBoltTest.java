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

package org.apache.metron.parsers.bolt;

import org.apache.log4j.Level;
import org.apache.metron.common.Constants;
import org.apache.metron.common.configuration.IndexingConfigurations;
import org.apache.metron.common.configuration.ParserConfigurations;
import org.apache.metron.common.configuration.SensorParserConfig;
import org.apache.metron.common.error.MetronError;
import org.apache.metron.common.writer.BulkMessageWriter;
import org.apache.metron.common.writer.BulkWriterResponse;
import org.apache.metron.common.writer.MessageWriter;
import org.apache.metron.test.bolt.BaseBoltTest;
import org.apache.metron.test.error.MetronErrorJSONMatcher;
import org.apache.metron.test.utils.UnitTestHelper;
import org.apache.metron.writer.BulkWriterComponent;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.tuple.Tuple;
import org.json.simple.JSONObject;
import org.junit.Test;
import org.mockito.Mock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class WriterBoltTest extends BaseBoltTest{
  @Mock
  protected TopologyContext topologyContext;

  @Mock
  protected OutputCollector outputCollector;

  @Mock
  private MessageWriter<JSONObject> writer;

  @Mock
  private BulkMessageWriter<JSONObject> batchWriter;

  private ParserConfigurations getConfigurations(int batchSize) {
    return new ParserConfigurations() {
          @Override
          public SensorParserConfig getSensorParserConfig(String sensorType) {
            return new SensorParserConfig() {
              @Override
              public Map<String, Object> getParserConfig() {
                return new HashMap<String, Object>() {{
                  put(IndexingConfigurations.BATCH_SIZE_CONF, batchSize);
                }};
              }
            };
          }
        };
  }
  @Test
  public void testBatchHappyPath() throws Exception {
    ParserConfigurations configurations = getConfigurations(5);
    String sensorType = "test";
    List<Tuple> tuples = new ArrayList<>();
    for(int i = 0;i < 5;++i) {
      Tuple t = mock(Tuple.class);
      when(t.getValueByField(eq("message"))).thenReturn(new JSONObject());
      tuples.add(t);
    }
    WriterBolt bolt = new WriterBolt(new WriterHandler(batchWriter), configurations, sensorType);
    bolt.prepare(new HashMap(), topologyContext, outputCollector);
    verify(batchWriter, times(1)).init(any(), any(), any());
    for(int i = 0;i < 4;++i) {
      Tuple t = tuples.get(i);
      bolt.execute(t);
      verify(outputCollector, times(0)).ack(t);
      verify(batchWriter, times(0)).write(eq(sensorType), any(), any(), any());
    }

    // Ensure the batch returns the good Tuples
    BulkWriterResponse writerResponse = new BulkWriterResponse();
    writerResponse.addAllSuccesses(tuples);
    when(batchWriter.write(any(), any(), any(), any())).thenReturn(writerResponse);

    bolt.execute(tuples.get(4));
    for(Tuple t : tuples) {
      verify(outputCollector, times(1)).ack(t);
    }
    verify(batchWriter, times(1)).write(eq(sensorType), any(), any(), any());
    verify(outputCollector, times(0)).reportError(any());
    verify(outputCollector, times(0)).fail(any());
  }

  @Test
  public void testNonBatchHappyPath() throws Exception {
    ParserConfigurations configurations = getConfigurations(1);
    String sensorType = "test";
    Tuple t = mock(Tuple.class);
    when(t.getValueByField(eq("message"))).thenReturn(new JSONObject());
    WriterBolt bolt = new WriterBolt(new WriterHandler(writer), configurations, sensorType);
    bolt.prepare(new HashMap(), topologyContext, outputCollector);
    verify(writer, times(1)).init();
    bolt.execute(t);
    verify(outputCollector, times(1)).ack(t);
    verify(writer, times(1)).write(eq(sensorType), any(), any(), any());
    verify(outputCollector, times(0)).reportError(any());
    verify(outputCollector, times(0)).fail(any());
  }
  @Test
  public void testNonBatchErrorPath() throws Exception {
    ParserConfigurations configurations = getConfigurations(1);
    String sensorType = "test";
    Tuple t = mock(Tuple.class);
    when(t.getValueByField(eq("message"))).thenThrow(new IllegalStateException());
    WriterBolt bolt = new WriterBolt(new WriterHandler(writer), configurations, sensorType);
    bolt.prepare(new HashMap(), topologyContext, outputCollector);
    verify(writer, times(1)).init();
    bolt.execute(t);
    verify(outputCollector, times(1)).ack(t);
    verify(writer, times(0)).write(eq(sensorType), any(), any(), any());
    verify(outputCollector, times(1)).reportError(any());
    verify(outputCollector, times(0)).fail(any());
  }
  @Test
  public void testNonBatchErrorPathErrorInWrite() throws Exception {
    ParserConfigurations configurations = getConfigurations(1);
    String sensorType = "test";
    Tuple t = mock(Tuple.class);
    when(t.toString()).thenReturn("tuple");
    when(t.getValueByField(eq("message"))).thenReturn(new JSONObject());
    WriterBolt bolt = new WriterBolt(new WriterHandler(writer), configurations, sensorType);
    bolt.prepare(new HashMap(), topologyContext, outputCollector);
    doThrow(new Exception("write error")).when(writer).write(any(), any(), any(), any());
    verify(writer, times(1)).init();
    bolt.execute(t);
    verify(outputCollector, times(1)).ack(t);
    verify(writer, times(1)).write(eq(sensorType), any(), any(), any());
    verify(outputCollector, times(1)).reportError(any());
    verify(outputCollector, times(0)).fail(any());

    MetronError error = new MetronError()
            .withErrorType(Constants.ErrorType.DEFAULT_ERROR)
            .withThrowable(new IllegalStateException("Unhandled bulk errors in response: {java.lang.Exception: write error=[tuple]}"))
            .withSensorType(sensorType)
            .addRawMessage(new JSONObject());
    verify(outputCollector, times(1)).emit(eq(Constants.ERROR_STREAM), argThat(new MetronErrorJSONMatcher(error.getJSONObject())));
  }

  @Test
  public void testBatchErrorPath() throws Exception {
    ParserConfigurations configurations = getConfigurations(5);
    String sensorType = "test";
    List<Tuple> tuples = new ArrayList<>();
    for(int i = 0;i < 4;++i) {
      Tuple t = mock(Tuple.class);
      when(t.getValueByField(eq("message"))).thenReturn(new JSONObject());
      tuples.add(t);
    }
    Tuple errorTuple = mock(Tuple.class);
    Tuple goodTuple = mock(Tuple.class);
    when(goodTuple.getValueByField(eq("message"))).thenReturn(new JSONObject());
    when(errorTuple.getValueByField(eq("message"))).thenThrow(new IllegalStateException());

    WriterBolt bolt = new WriterBolt(new WriterHandler(batchWriter), configurations, sensorType);
    bolt.prepare(new HashMap(), topologyContext, outputCollector);
    verify(batchWriter, times(1)).init(any(), any(), any());

    for(int i = 0;i < 4;++i) {
      Tuple t = tuples.get(i);
      bolt.execute(t);
      verify(outputCollector, times(0)).ack(t);
      verify(batchWriter, times(0)).write(eq(sensorType), any(), any(), any());
    }

    // Add the good tuples.  Do not add the error tuple, because this is testing an exception on access, not a failure on write.
    BulkWriterResponse writerResponse = new BulkWriterResponse();
    writerResponse.addAllSuccesses(tuples);
    writerResponse.addSuccess(goodTuple);
    when(batchWriter.write(any(), any(), any(), any())).thenReturn(writerResponse);

    bolt.execute(errorTuple);
    for(Tuple t : tuples) {
      verify(outputCollector, times(0)).ack(t);
    }
    bolt.execute(goodTuple);
    for(Tuple t : tuples) {
      verify(outputCollector, times(1)).ack(t);
    }
    verify(outputCollector, times(1)).ack(goodTuple);
    verify(batchWriter, times(1)).write(eq(sensorType), any(), any(), any());
    verify(outputCollector, times(1)).reportError(any());
    verify(outputCollector, times(0)).fail(any());
  }

  @Test
  public void testBatchErrorWriteFailure() throws Exception {
    ParserConfigurations configurations = getConfigurations(6);
    String sensorType = "test";
    List<Tuple> tuples = new ArrayList<>();
    for(int i = 0;i < 4;++i) {
      Tuple t = mock(Tuple.class);
      when(t.getValueByField(eq("message"))).thenReturn(new JSONObject());
      tuples.add(t);
    }
    Tuple errorTuple = mock(Tuple.class);
    Tuple goodTuple = mock(Tuple.class);
    when(goodTuple.getValueByField(eq("message"))).thenReturn(new JSONObject());
    when(errorTuple.getValueByField(eq("message"))).thenReturn(new JSONObject());

    WriterBolt bolt = new WriterBolt(new WriterHandler(batchWriter), configurations, sensorType);
    bolt.prepare(new HashMap(), topologyContext, outputCollector);
    verify(batchWriter, times(1)).init(any(), any(), any());

    for(int i = 0;i < 4;++i) {
      Tuple t = tuples.get(i);
      bolt.execute(t);
      verify(outputCollector, times(0)).ack(t);
      verify(batchWriter, times(0)).write(eq(sensorType), any(), any(), any());
    }

    // Add both the good and error Tuples. This simulates a seemingly good Tuple that fails on write.
    BulkWriterResponse writerResponse = new BulkWriterResponse();
    writerResponse.addAllSuccesses(tuples);
    writerResponse.addSuccess(goodTuple);
    writerResponse.addError(new IllegalStateException(), errorTuple);
    when(batchWriter.write(any(), any(), any(), any())).thenReturn(writerResponse);
    bolt.execute(errorTuple);
    for(Tuple t : tuples) {
      verify(outputCollector, times(0)).ack(t);
    }
    UnitTestHelper.setLog4jLevel(BulkWriterComponent.class, Level.FATAL);
    bolt.execute(goodTuple);
    UnitTestHelper.setLog4jLevel(BulkWriterComponent.class, Level.ERROR);
    for(Tuple t : tuples) {
      verify(outputCollector, times(1)).ack(t);
    }
    verify(outputCollector, times(1)).ack(goodTuple);
    verify(batchWriter, times(1)).write(eq(sensorType), any(), any(), any());
    verify(outputCollector, times(1)).reportError(any());
    verify(outputCollector, times(0)).fail(any());
  }

  @Test
  public void testBatchErrorPathExceptionInWrite() throws Exception {
    ParserConfigurations configurations = getConfigurations(5);
    String sensorType = "test";
    List<Tuple> tuples = new ArrayList<>();
    for(int i = 0;i < 4;++i) {
      Tuple t = mock(Tuple.class);
      when(t.getValueByField(eq("message"))).thenReturn(new JSONObject());
      tuples.add(t);
    }
    Tuple goodTuple = mock(Tuple.class);
    when(goodTuple.getValueByField(eq("message"))).thenReturn(new JSONObject());

    WriterBolt bolt = new WriterBolt(new WriterHandler(batchWriter), configurations, sensorType);
    bolt.prepare(new HashMap(), topologyContext, outputCollector);
    doThrow(new Exception()).when(batchWriter).write(any(), any(), any(), any());
    verify(batchWriter, times(1)).init(any(), any(), any());
    for(int i = 0;i < 4;++i) {
      Tuple t = tuples.get(i);
      bolt.execute(t);
      verify(outputCollector, times(0)).ack(t);
      verify(batchWriter, times(0)).write(eq(sensorType), any(), any(), any());
    }
    UnitTestHelper.setLog4jLevel(BulkWriterComponent.class, Level.FATAL);
    bolt.execute(goodTuple);
    UnitTestHelper.setLog4jLevel(BulkWriterComponent.class, Level.ERROR);
    for(Tuple t : tuples) {
      verify(outputCollector, times(1)).ack(t);
    }
    verify(batchWriter, times(1)).write(eq(sensorType), any(), any(), any());
    verify(outputCollector, times(1)).ack(goodTuple);
    verify(outputCollector, times(1)).reportError(any());
    verify(outputCollector, times(0)).fail(any());
  }
}
