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

import org.apache.metron.common.Constants;
import org.apache.metron.common.configuration.*;

import org.apache.metron.common.error.MetronError;
import org.apache.metron.test.error.MetronErrorJSONMatcher;
import org.apache.metron.test.utils.UnitTestHelper;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.tuple.Tuple;
import com.google.common.collect.ImmutableList;
import org.apache.metron.common.configuration.writer.ParserWriterConfiguration;
import org.apache.metron.common.configuration.writer.WriterConfiguration;
import org.apache.metron.common.dsl.Context;
import org.apache.metron.common.writer.BulkMessageWriter;
import org.adrianwalker.multilinestring.Multiline;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.metron.common.configuration.ParserConfigurations;
import org.apache.metron.common.configuration.SensorParserConfig;
import org.apache.metron.common.utils.ErrorUtils;
import org.apache.metron.common.writer.BulkWriterResponse;
import org.apache.metron.parsers.BasicParser;
import org.apache.metron.parsers.csv.CSVParser;
import org.apache.metron.test.bolt.BaseBoltTest;
import org.apache.metron.parsers.interfaces.MessageFilter;
import org.apache.metron.parsers.interfaces.MessageParser;
import org.apache.metron.common.writer.MessageWriter;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mock;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

public class ParserBoltTest extends BaseBoltTest {

  @Mock
  private MessageParser<JSONObject> parser;

  @Mock
  private MessageWriter<JSONObject> writer;

  @Mock
  private BulkMessageWriter<JSONObject> batchWriter;

  @Mock
  private MessageFilter<JSONObject> filter;

  @Mock
  private Tuple t1;

  @Mock
  private Tuple t2;

  @Mock
  private Tuple t3;

  @Mock
  private Tuple t4;

  @Mock
  private Tuple t5;

  private static class RecordingWriter implements BulkMessageWriter<JSONObject> {
    List<JSONObject> records = new ArrayList<>();

    @Override
    public void init(Map stormConf, TopologyContext topologyContext, WriterConfiguration config) throws Exception {

    }

    @Override
    public BulkWriterResponse write(String sensorType, WriterConfiguration configurations, Iterable<Tuple> tuples, List<JSONObject> messages) throws Exception {
      records.addAll(messages);
      BulkWriterResponse ret = new BulkWriterResponse();
      ret.addAllSuccesses(tuples);
      return ret;
    }

    @Override
    public String getName() {
      return "recording";
    }

    @Override
    public void close() throws Exception {

    }

    public List<JSONObject> getRecords() {
      return records;
    }
  }


  @Test
  public void testEmpty() throws Exception {
    String sensorType = "yaf";
    ParserBolt parserBolt = new ParserBolt("zookeeperUrl", sensorType, parser, new WriterHandler(writer)) {
      @Override
      protected ParserConfigurations defaultConfigurations() {
        return new ParserConfigurations() {
          @Override
          public SensorParserConfig getSensorParserConfig(String sensorType) {
            return new SensorParserConfig() {
              @Override
              public Map<String, Object> getParserConfig() {
                return new HashMap<String, Object>() {{
                }};
              }
            };
          }
        };
      }

    };

    parserBolt.setCuratorFramework(client);
    parserBolt.setTreeCache(cache);
    parserBolt.prepare(new HashMap(), topologyContext, outputCollector);
    verify(parser, times(1)).init();
    verify(writer, times(1)).init();
    byte[] sampleBinary = "some binary message".getBytes();

    when(tuple.getBinary(0)).thenReturn(sampleBinary);
    when(parser.parseOptional(sampleBinary)).thenReturn(null);
    parserBolt.execute(tuple);
    verify(parser, times(0)).validate(any());
    verify(writer, times(0)).write(eq(sensorType), any(ParserWriterConfiguration.class), eq(tuple), any());
    verify(outputCollector, times(1)).ack(tuple);

    MetronError error = new MetronError()
            .withErrorType(Constants.ErrorType.PARSER_ERROR)
            .withThrowable(new NullPointerException())
            .withSensorType(sensorType)
            .addRawMessage(sampleBinary);
    verify(outputCollector, times(1)).emit(eq(Constants.ERROR_STREAM), argThat(new MetronErrorJSONMatcher(error.getJSONObject())));
  }

  @Test
  public void testInvalid() throws Exception {
    String sensorType = "yaf";
    ParserBolt parserBolt = new ParserBolt("zookeeperUrl", sensorType, parser, new WriterHandler(writer)) {
      @Override
      protected ParserConfigurations defaultConfigurations() {
        return new ParserConfigurations() {
          @Override
          public SensorParserConfig getSensorParserConfig(String sensorType) {
            return new SensorParserConfig() {
              @Override
              public Map<String, Object> getParserConfig() {
                return new HashMap<String, Object>() {{
                }};
              }


            };
          }
        };
      }
    };

    buildGlobalConfig(parserBolt);

    parserBolt.setCuratorFramework(client);
    parserBolt.setTreeCache(cache);
    parserBolt.prepare(new HashMap(), topologyContext, outputCollector);
    byte[] sampleBinary = "some binary message".getBytes();

    when(tuple.getBinary(0)).thenReturn(sampleBinary);
    JSONObject parsedMessage = new JSONObject();
    parsedMessage.put("field", "invalidValue");
    parsedMessage.put("guid", "this-is-unique-identifier-for-tuple");
    List<JSONObject> messageList = new ArrayList<>();
    messageList.add(parsedMessage);
    when(parser.parseOptional(sampleBinary)).thenReturn(Optional.of(messageList));
    when(parser.validate(parsedMessage)).thenReturn(true);
    parserBolt.execute(tuple);

    MetronError error = new MetronError()
            .withErrorType(Constants.ErrorType.PARSER_INVALID)
            .withSensorType(sensorType)
            .withErrorFields(new HashSet<String>() {{ add("field"); }})
            .addRawMessage(new JSONObject(){{
              put("field", "invalidValue");
              put("source.type", "yaf");
              put("guid", "this-is-unique-identifier-for-tuple");
            }});
    verify(outputCollector, times(1)).emit(eq(Constants.ERROR_STREAM), argThat(new MetronErrorJSONMatcher(error.getJSONObject())));
  }

  @Test
  public void test() throws Exception {

    String sensorType = "yaf";
    ParserBolt parserBolt = new ParserBolt("zookeeperUrl", sensorType, parser, new WriterHandler(writer)) {
      @Override
      protected ParserConfigurations defaultConfigurations() {
        return new ParserConfigurations() {
          @Override
          public SensorParserConfig getSensorParserConfig(String sensorType) {
            return new SensorParserConfig() {
              @Override
              public Map<String, Object> getParserConfig() {
                return new HashMap<String, Object>() {{
                }};
              }
            };
          }
        };
      }
    };
    parserBolt.setCuratorFramework(client);
    parserBolt.setTreeCache(cache);
    parserBolt.prepare(new HashMap(), topologyContext, outputCollector);
    verify(parser, times(1)).init();
    verify(writer, times(1)).init();
    byte[] sampleBinary = "some binary message".getBytes();
    JSONParser jsonParser = new JSONParser();
    final JSONObject sampleMessage1 = (JSONObject) jsonParser.parse("{ \"field1\":\"value1\", \"guid\": \"this-is-unique-identifier-for-tuple\" }");
    final JSONObject sampleMessage2 = (JSONObject) jsonParser.parse("{ \"field2\":\"value2\", \"guid\": \"this-is-unique-identifier-for-tuple\" }");
    List<JSONObject> messages = new ArrayList<JSONObject>() {{
      add(sampleMessage1);
      add(sampleMessage2);
    }};
    final JSONObject finalMessage1 = (JSONObject) jsonParser.parse("{ \"field1\":\"value1\", \"source.type\":\"" + sensorType + "\", \"guid\": \"this-is-unique-identifier-for-tuple\" }");
    final JSONObject finalMessage2 = (JSONObject) jsonParser.parse("{ \"field2\":\"value2\", \"source.type\":\"" + sensorType + "\", \"guid\": \"this-is-unique-identifier-for-tuple\" }");
    when(tuple.getBinary(0)).thenReturn(sampleBinary);
    when(parser.parseOptional(sampleBinary)).thenReturn(Optional.of(messages));
    when(parser.validate(eq(messages.get(0)))).thenReturn(true);
    when(parser.validate(eq(messages.get(1)))).thenReturn(false);
    parserBolt.execute(tuple);
    verify(writer, times(1)).write(eq(sensorType), any(ParserWriterConfiguration.class), eq(tuple), eq(finalMessage1));
    verify(outputCollector, times(1)).ack(tuple);
    when(parser.validate(eq(messages.get(0)))).thenReturn(true);
    when(parser.validate(eq(messages.get(1)))).thenReturn(true);
    when(filter.emitTuple(eq(messages.get(0)), any())).thenReturn(false);
    when(filter.emitTuple(eq(messages.get(1)), any())).thenReturn(true);
    parserBolt.withMessageFilter(filter);
    parserBolt.execute(tuple);
    verify(writer, times(1)).write(eq(sensorType), any(ParserWriterConfiguration.class), eq(tuple), eq(finalMessage2));
    verify(outputCollector, times(2)).ack(tuple);
    doThrow(new Exception()).when(writer).write(eq(sensorType), any(ParserWriterConfiguration.class), eq(tuple), eq(finalMessage2));
    parserBolt.execute(tuple);
    verify(outputCollector, times(1)).reportError(any(Throwable.class));
  }
@Test
public void testImplicitBatchOfOne() throws Exception {

  String sensorType = "yaf";

  ParserBolt parserBolt = new ParserBolt("zookeeperUrl", sensorType, parser, new WriterHandler(batchWriter)) {
    @Override
    protected ParserConfigurations defaultConfigurations() {
      return new ParserConfigurations() {
        @Override
        public SensorParserConfig getSensorParserConfig(String sensorType) {
          return new SensorParserConfig() {
            @Override
            public Map<String, Object> getParserConfig() {
              return new HashMap<String, Object>() {{
              }};
            }
          };
        }
      };
    }
  };

  parserBolt.setCuratorFramework(client);
  parserBolt.setTreeCache(cache);
  parserBolt.prepare(new HashMap(), topologyContext, outputCollector);
  verify(parser, times(1)).init();
  verify(batchWriter, times(1)).init(any(), any(), any());
  when(parser.validate(any())).thenReturn(true);
  when(parser.parseOptional(any())).thenReturn(Optional.of(ImmutableList.of(new JSONObject())));
  when(filter.emitTuple(any(), any(Context.class))).thenReturn(true);
  BulkWriterResponse response = new BulkWriterResponse();
  response.addSuccess(t1);
  when(batchWriter.write(eq(sensorType), any(WriterConfiguration.class), eq(Collections.singleton(t1)), any())).thenReturn(response);
  parserBolt.withMessageFilter(filter);
  parserBolt.execute(t1);
  verify(outputCollector, times(1)).ack(t1);
}

  /**
   {
    "filterClassName" : "STELLAR"
   ,"parserConfig" : {
    "filter.query" : "exists(field1)"
    }
   }
   */
  @Multiline
  public static String sensorParserConfig;

  /**
   * Tests to ensure that a message that is unfiltered results in one write and an ack.
   * @throws Exception
   */
  @Test
  public void testFilterSuccess() throws Exception {
    String sensorType = "yaf";

    ParserBolt parserBolt = new ParserBolt("zookeeperUrl", sensorType, parser, new WriterHandler(batchWriter)) {
      @Override
      protected SensorParserConfig getSensorParserConfig() {
        try {
          return SensorParserConfig.fromBytes(Bytes.toBytes(sensorParserConfig));
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    };

    parserBolt.setCuratorFramework(client);
    parserBolt.setTreeCache(cache);
    parserBolt.prepare(new HashMap(), topologyContext, outputCollector);
    verify(parser, times(1)).init();
    verify(batchWriter, times(1)).init(any(), any(), any());
    BulkWriterResponse successResponse = mock(BulkWriterResponse.class);
    when(successResponse.getSuccesses()).thenReturn(ImmutableList.of(t1));
    when(batchWriter.write(any(), any(), any(), any())).thenReturn(successResponse);
    when(parser.validate(any())).thenReturn(true);
    when(parser.parseOptional(any())).thenReturn(Optional.of(ImmutableList.of(new JSONObject(new HashMap<String, Object>() {{
      put("field1", "blah");
    }}))));
    parserBolt.execute(t1);
    verify(batchWriter, times(1)).write(any(), any(), any(), any());
    verify(outputCollector, times(1)).ack(t1);
  }


  /**
   * Tests to ensure that a message filtered out results in no writes, but an ack.
   * @throws Exception
   */
  @Test
  public void testFilterFailure() throws Exception {
    String sensorType = "yaf";

    ParserBolt parserBolt = new ParserBolt("zookeeperUrl", sensorType, parser, new WriterHandler(batchWriter)) {
      @Override
      protected SensorParserConfig getSensorParserConfig() {
        try {
          return SensorParserConfig.fromBytes(Bytes.toBytes(sensorParserConfig));
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    };

    parserBolt.setCuratorFramework(client);
    parserBolt.setTreeCache(cache);
    parserBolt.prepare(new HashMap(), topologyContext, outputCollector);
    verify(parser, times(1)).init();
    verify(batchWriter, times(1)).init(any(), any(), any());
    when(parser.validate(any())).thenReturn(true);
    when(parser.parseOptional(any())).thenReturn(Optional.of(ImmutableList.of(new JSONObject(new HashMap<String, Object>() {{
      put("field2", "blah");
    }}))));
    parserBolt.execute(t1);
    verify(batchWriter, times(0)).write(any(), any(), any(), any());
    verify(outputCollector, times(1)).ack(t1);
  }
  /**
  {
     "sensorTopic":"dummy"
     ,"parserConfig": {
      "batchSize" : 1
     }
      ,"fieldTransformations" : [
          {
           "transformation" : "STELLAR"
          ,"output" : "timestamp"
          ,"config" : {
            "timestamp" : "TO_EPOCH_TIMESTAMP(timestampstr, 'yyyy-MM-dd HH:mm:ss', 'UTC')"
                      }
          }
                               ]
   }
   */
  @Multiline
  public static String csvWithFieldTransformations;

  @Test
  public void testFieldTransformationPriorToValidation() {
    String sensorType = "dummy";
    RecordingWriter recordingWriter = new RecordingWriter();
    //create a parser which acts like a basic parser but returns no timestamp field.
    BasicParser dummyParser = new BasicParser() {
      @Override
      public void init() {

      }

      @Override
      public List<JSONObject> parse(byte[] rawMessage) {
        return ImmutableList.of(new JSONObject() {{
                put("data", "foo");
                put("timestampstr", "2016-01-05 17:02:30");
                put("original_string", "blah");
              }});
      }

      @Override
      public void configure(Map<String, Object> config) {

      }
    };
    ParserBolt parserBolt = new ParserBolt("zookeeperUrl", sensorType, dummyParser, new WriterHandler(recordingWriter)) {
      @Override
      protected SensorParserConfig getSensorParserConfig() {
        try {
          return SensorParserConfig.fromBytes(Bytes.toBytes(csvWithFieldTransformations));
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    };

    parserBolt.setCuratorFramework(client);
    parserBolt.setTreeCache(cache);
    parserBolt.prepare(new HashMap(), topologyContext, outputCollector);
    when(t1.getBinary(0)).thenReturn(new byte[] {});
    parserBolt.execute(t1);
    Assert.assertEquals(1, recordingWriter.getRecords().size());
    long expected = 1452013350000L;
    Assert.assertEquals(expected, recordingWriter.getRecords().get(0).get("timestamp"));
  }

  @Test
  public void testBatchOfOne() throws Exception {

    String sensorType = "yaf";

    ParserBolt parserBolt = new ParserBolt("zookeeperUrl", sensorType, parser, new WriterHandler(batchWriter)) {
      @Override
      protected ParserConfigurations defaultConfigurations() {
        return new ParserConfigurations() {
          @Override
          public SensorParserConfig getSensorParserConfig(String sensorType) {
            return new SensorParserConfig() {
              @Override
              public Map<String, Object> getParserConfig() {
                return new HashMap<String, Object>() {{
                  put(IndexingConfigurations.BATCH_SIZE_CONF, "1");
                }};
              }
            };
          }
        };
      }
    };

    parserBolt.setCuratorFramework(client);
    parserBolt.setTreeCache(cache);
    parserBolt.prepare(new HashMap(), topologyContext, outputCollector);
    verify(parser, times(1)).init();
    verify(batchWriter, times(1)).init(any(), any(), any());
    when(parser.validate(any())).thenReturn(true);
    when(parser.parseOptional(any())).thenReturn(Optional.of(ImmutableList.of(new JSONObject())));
    when(filter.emitTuple(any(), any(Context.class))).thenReturn(true);
    BulkWriterResponse response = new BulkWriterResponse();
    response.addSuccess(t1);
    when(batchWriter.write(eq(sensorType), any(WriterConfiguration.class), eq(Collections.singleton(t1)), any())).thenReturn(response);
    parserBolt.withMessageFilter(filter);
    parserBolt.execute(t1);
    verify(outputCollector, times(1)).ack(t1);
  }
  @Test
  public void testBatchOfFive() throws Exception {

    String sensorType = "yaf";

    ParserBolt parserBolt = new ParserBolt("zookeeperUrl", sensorType, parser, new WriterHandler(batchWriter)) {
      @Override
      protected ParserConfigurations defaultConfigurations() {
        return new ParserConfigurations() {
          @Override
          public SensorParserConfig getSensorParserConfig(String sensorType) {
            return new SensorParserConfig() {
              @Override
              public Map<String, Object> getParserConfig() {
                return new HashMap<String, Object>() {{
                  put(IndexingConfigurations.BATCH_SIZE_CONF, 5);
                }};
              }
            };
          }
        };
      }
    };

    parserBolt.setCuratorFramework(client);
    parserBolt.setTreeCache(cache);
    parserBolt.prepare(new HashMap(), topologyContext, outputCollector);
    verify(parser, times(1)).init();
    verify(batchWriter, times(1)).init(any(), any(), any());
    when(parser.validate(any())).thenReturn(true);
    when(parser.parseOptional(any())).thenReturn(Optional.of(ImmutableList.of(new JSONObject())));
    when(filter.emitTuple(any(), any(Context.class))).thenReturn(true);
    Set<Tuple> tuples = Stream.of(t1, t2, t3, t4, t5).collect(Collectors.toSet());
    BulkWriterResponse response = new BulkWriterResponse();
    response.addAllSuccesses(tuples);
    when(batchWriter.write(eq(sensorType), any(WriterConfiguration.class), eq(tuples), any())).thenReturn(response);
    parserBolt.withMessageFilter(filter);
    writeNonBatch(outputCollector, parserBolt, t1);
    writeNonBatch(outputCollector, parserBolt, t2);
    writeNonBatch(outputCollector, parserBolt, t3);
    writeNonBatch(outputCollector, parserBolt, t4);
    parserBolt.execute(t5);
    verify(outputCollector, times(1)).ack(t1);
    verify(outputCollector, times(1)).ack(t2);
    verify(outputCollector, times(1)).ack(t3);
    verify(outputCollector, times(1)).ack(t4);
    verify(outputCollector, times(1)).ack(t5);


  }
  @Test
  public void testBatchOfFiveWithError() throws Exception {

    String sensorType = "yaf";
    ParserBolt parserBolt = new ParserBolt("zookeeperUrl", sensorType, parser, new WriterHandler(batchWriter)) {
      @Override
      protected ParserConfigurations defaultConfigurations() {
        return new ParserConfigurations() {
          @Override
          public SensorParserConfig getSensorParserConfig(String sensorType) {
            return new SensorParserConfig() {
              @Override
              public Map<String, Object> getParserConfig() {
                return new HashMap<String, Object>() {{
                  put(IndexingConfigurations.BATCH_SIZE_CONF, 5);
                }};
              }
            };
          }
        };
      }
    };

    parserBolt.setCuratorFramework(client);
    parserBolt.setTreeCache(cache);
    parserBolt.prepare(new HashMap(), topologyContext, outputCollector);
    verify(parser, times(1)).init();
    verify(batchWriter, times(1)).init(any(), any(), any());

    doThrow(new Exception()).when(batchWriter).write(any(), any(), any(), any());
    when(parser.validate(any())).thenReturn(true);
    when(parser.parse(any())).thenReturn(ImmutableList.of(new JSONObject()));
    when(filter.emitTuple(any(), any(Context.class))).thenReturn(true);
    parserBolt.withMessageFilter(filter);
    parserBolt.execute(t1);
    parserBolt.execute(t2);
    parserBolt.execute(t3);
    parserBolt.execute(t4);
    parserBolt.execute(t5);
    verify(outputCollector, times(1)).ack(t1);
    verify(outputCollector, times(1)).ack(t2);
    verify(outputCollector, times(1)).ack(t3);
    verify(outputCollector, times(1)).ack(t4);
    verify(outputCollector, times(1)).ack(t5);

  }

  protected void buildGlobalConfig(ParserBolt parserBolt) {
    HashMap<String, Object> globalConfig = new HashMap<>();
    Map<String, Object> fieldValidation = new HashMap<>();
    fieldValidation.put("input", Arrays.asList("field"));
    fieldValidation.put("validation", "STELLAR");
    fieldValidation.put("config", new HashMap<String, String>(){{ put("condition", "field != 'invalidValue'"); }});
    globalConfig.put("fieldValidations", Arrays.asList(fieldValidation));
    parserBolt.getConfigurations().updateGlobalConfig(globalConfig);
  }

  private static void writeNonBatch(OutputCollector collector, ParserBolt bolt, Tuple t) {
    bolt.execute(t);
  }

}
