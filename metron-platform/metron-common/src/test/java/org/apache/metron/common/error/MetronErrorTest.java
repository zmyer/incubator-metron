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
package org.apache.metron.common.error;

import com.google.common.collect.Sets;
import com.google.common.primitives.Bytes;
import org.apache.metron.common.Constants;
import org.json.simple.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;

import static org.apache.metron.common.Constants.ErrorFields.RAW_MESSAGE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MetronErrorTest {

  private JSONObject message1 = new JSONObject();
  private JSONObject message2 = new JSONObject();

  @Before
  public void setup() {
    message1.put("value", "message1");
    message2.put("value", "message2");
  }

  @Test
  public void getJSONObjectShouldReturnBasicInformation() {
    MetronError error = new MetronError()
            .withMessage("test message")
            .withErrorType(Constants.ErrorType.PARSER_ERROR)
            .withSensorType("sensorType");

    JSONObject errorJSON = error.getJSONObject();
    assertEquals("test message", errorJSON.get(Constants.ErrorFields.MESSAGE.getName()));
    assertEquals(Constants.ErrorType.PARSER_ERROR.getType(), errorJSON.get(Constants.ErrorFields.ERROR_TYPE.getName()));
    assertEquals("error", errorJSON.get(Constants.SENSOR_TYPE));
    assertEquals("sensorType", errorJSON.get(Constants.ErrorFields.FAILED_SENSOR_TYPE.getName()));
    assertTrue(((String) errorJSON.get(Constants.ErrorFields.HOSTNAME.getName())).length() > 0);
    assertTrue(((long) errorJSON.get(Constants.ErrorFields.TIMESTAMP.getName())) > 0);
  }

  @Test
  public void getJSONObjectShouldHandleThrowable() {
    Throwable e = new Exception("test exception");
    MetronError error = new MetronError().withThrowable(e);

    JSONObject errorJSON = error.getJSONObject();
    assertEquals("java.lang.Exception: test exception", errorJSON.get(Constants.ErrorFields.EXCEPTION.getName()));
    assertTrue(((String) errorJSON.get(Constants.ErrorFields.STACK.getName())).startsWith("java.lang.Exception: test exception"));
    assertEquals(e.getMessage(), errorJSON.get(Constants.ErrorFields.MESSAGE.getName()));
  }

  @Test
  public void getJSONObjectShouldIncludeRawMessages() {
    JSONObject message1 = new JSONObject();
    JSONObject message2 = new JSONObject();
    message1.put("value", "message1");
    message2.put("value", "message2");
    MetronError error = new MetronError().withRawMessages(Arrays.asList(message1, message2));

    JSONObject errorJSON = error.getJSONObject();

    assertEquals("{\"value\":\"message1\"}", errorJSON.get(Constants.ErrorFields.RAW_MESSAGE.getName() + "_0"));
    assertEquals("{\"value\":\"message2\"}", errorJSON.get(Constants.ErrorFields.RAW_MESSAGE.getName() + "_1"));

    error = new MetronError().addRawMessage("raw message".getBytes());
    errorJSON = error.getJSONObject();
    assertEquals("raw message", errorJSON.get(Constants.ErrorFields.RAW_MESSAGE.getName()));
    // It's unclear if we need a rawMessageBytes field so commenting out for now
    //assertEquals(Bytes.asList("raw message".getBytes()), errorJSON.get(Constants.ErrorFields.RAW_MESSAGE_BYTES.getName()));
    assertEquals("3b02cb29676bc448c69da1ec5eef7c89f4d6dc6a5a7ce0296ea25b207eea36be", errorJSON.get(Constants.ErrorFields.ERROR_HASH.getName()));

    error = new MetronError().addRawMessage(message1);
    errorJSON = error.getJSONObject();
    assertEquals("{\"value\":\"message1\"}", errorJSON.get(Constants.ErrorFields.RAW_MESSAGE.getName()));
    assertEquals("e8aaf87c8494d345aac2d612ffd94fcf0b98c975fe6c4b991e2f8280a3a0bd10", errorJSON.get(Constants.ErrorFields.ERROR_HASH.getName()));
  }

  @Test
  public void getJSONObjectShouldIncludeErrorFields() {
    JSONObject message = new JSONObject();
    message.put("field1", "value1");
    message.put("field2", "value2");

    MetronError error = new MetronError().addRawMessage(message).withErrorFields(Sets.newHashSet("field1", "field2"));

    JSONObject errorJSON = error.getJSONObject();
    assertEquals(Sets.newHashSet("field1", "field2"), Sets.newHashSet(((String) errorJSON.get(Constants.ErrorFields.ERROR_FIELDS.getName())).split(",")));
    assertEquals("04a2629c39e098c3944be85f35c75876598f2b44b8e5e3f52c59fa1ac182817c", errorJSON.get(Constants.ErrorFields.ERROR_HASH.getName()));
  }
}
