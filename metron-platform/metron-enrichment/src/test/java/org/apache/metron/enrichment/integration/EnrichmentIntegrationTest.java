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
package org.apache.metron.enrichment.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.client.HTableInterface;
import org.apache.metron.TestConstants;
import org.apache.metron.common.Constants;
import org.apache.metron.common.utils.JSONUtils;
import org.apache.metron.enrichment.adapters.geo.GeoLiteDatabase;
import org.apache.metron.enrichment.bolt.ErrorEnrichmentBolt;
import org.apache.metron.enrichment.bolt.ThreatIntelJoinBolt;
import org.apache.metron.enrichment.converter.EnrichmentHelper;
import org.apache.metron.enrichment.converter.EnrichmentKey;
import org.apache.metron.enrichment.converter.EnrichmentValue;
import org.apache.metron.enrichment.integration.components.ConfigUploadComponent;
import org.apache.metron.enrichment.lookup.LookupKV;
import org.apache.metron.enrichment.lookup.accesstracker.PersistentBloomTrackerCreator;
import org.apache.metron.enrichment.stellar.SimpleHBaseEnrichmentFunctions;
import org.apache.metron.hbase.TableProvider;
import org.apache.metron.integration.BaseIntegrationTest;
import org.apache.metron.integration.ComponentRunner;
import org.apache.metron.integration.Processor;
import org.apache.metron.integration.ProcessorResult;
import org.apache.metron.integration.components.FluxTopologyComponent;
import org.apache.metron.integration.components.KafkaComponent;
import org.apache.metron.integration.components.ZKServerComponent;
import org.apache.metron.integration.processors.KafkaMessageSet;
import org.apache.metron.integration.processors.KafkaProcessor;
import org.apache.metron.integration.utils.TestUtils;
import org.apache.metron.test.mock.MockHTable;
import org.apache.metron.test.utils.UnitTestHelper;
import org.json.simple.parser.ParseException;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Stream;

import static org.apache.metron.enrichment.bolt.ThreatIntelJoinBolt.*;

public class EnrichmentIntegrationTest extends BaseIntegrationTest {
  private static final String ERROR_TOPIC = "enrichment_error";
  private static final String SRC_IP = "ip_src_addr";
  private static final String DST_IP = "ip_dst_addr";
  private static final String MALICIOUS_IP_TYPE = "malicious_ip";
  private static final String PLAYFUL_CLASSIFICATION_TYPE = "playful_classification";
  private static final Map<String, Object> PLAYFUL_ENRICHMENT = new HashMap<String, Object>() {{
    put("orientation", "north");
  }};

  public static final String DEFAULT_COUNTRY = "test country";
  public static final String DEFAULT_CITY = "test city";
  public static final String DEFAULT_POSTAL_CODE = "test postalCode";
  public static final String DEFAULT_LATITUDE = "test latitude";
  public static final String DEFAULT_LONGITUDE = "test longitude";
  public static final String DEFAULT_DMACODE= "test dmaCode";
  public static final String DEFAULT_LOCATION_POINT= Joiner.on(',').join(DEFAULT_LATITUDE,DEFAULT_LONGITUDE);

  protected String fluxPath = "../metron-enrichment/src/main/flux/enrichment/test.yaml";
  protected String sampleParsedPath = TestConstants.SAMPLE_DATA_PARSED_PATH + "TestExampleParsed";
  private final List<byte[]> inputMessages = getInputMessages(sampleParsedPath);

  private static File geoHdfsFile;

  public static class Provider implements TableProvider, Serializable {
    MockHTable.Provider  provider = new MockHTable.Provider();
    @Override
    public HTableInterface getTable(Configuration config, String tableName) throws IOException {
      return provider.getTable(config, tableName);
    }
  }

  private static List<byte[]> getInputMessages(String path){
    try{
      return TestUtils.readSampleData(path);
    }catch(IOException ioe){
      return null;
    }
  }

  @BeforeClass
  public static void setupOnce() throws ParseException {
    String baseDir = UnitTestHelper.findDir("GeoLite");
    geoHdfsFile = new File(new File(baseDir), "GeoIP2-City-Test.mmdb.gz");
  }

  @Test
  public void test() throws Exception {
    final String cf = "cf";
    final String trackerHBaseTableName = "tracker";
    final String threatIntelTableName = "threat_intel";
    final String enrichmentsTableName = "enrichments";
    final Properties topologyProperties = new Properties() {{
      setProperty("org.apache.metron.enrichment.host.known_hosts", "[{\"ip\":\"10.1.128.236\", \"local\":\"YES\", \"type\":\"webserver\", \"asset_value\" : \"important\"},\n" +
              "{\"ip\":\"10.1.128.237\", \"local\":\"UNKNOWN\", \"type\":\"unknown\", \"asset_value\" : \"important\"},\n" +
              "{\"ip\":\"10.60.10.254\", \"local\":\"YES\", \"type\":\"printer\", \"asset_value\" : \"important\"},\n" +
              "{\"ip\":\"10.0.2.15\", \"local\":\"YES\", \"type\":\"printer\", \"asset_value\" : \"important\"}]");
      setProperty("hbase.provider.impl", "" + Provider.class.getName());
      setProperty("threat.intel.tracker.table", trackerHBaseTableName);
      setProperty("threat.intel.tracker.cf", cf);
      setProperty("threat.intel.simple.hbase.table", threatIntelTableName);
      setProperty("threat.intel.simple.hbase.cf", cf);
      setProperty("enrichment.simple.hbase.table", enrichmentsTableName);
      setProperty("enrichment.simple.hbase.cf", cf);
      setProperty("enrichment.output.topic", Constants.INDEXING_TOPIC);
      setProperty("enrichment.error.topic", ERROR_TOPIC);
      setProperty("kafka.security.protocol", "PLAINTEXT");
      setProperty("storm.auto.credentials", "[]");
    }};
    final ZKServerComponent zkServerComponent = getZKServerComponent(topologyProperties);
    final KafkaComponent kafkaComponent = getKafkaComponent(topologyProperties, new ArrayList<KafkaComponent.Topic>() {{
      add(new KafkaComponent.Topic(Constants.ENRICHMENT_TOPIC, 1));
      add(new KafkaComponent.Topic(Constants.INDEXING_TOPIC, 1));
      add(new KafkaComponent.Topic(ERROR_TOPIC, 1));
    }});
    String globalConfigStr = null;
    {
      File globalConfig = new File(new File(TestConstants.SAMPLE_CONFIG_PATH), "global.json");
      Map<String, Object> config = JSONUtils.INSTANCE.load(globalConfig, new TypeReference<Map<String, Object>>() {
      });
      config.put(SimpleHBaseEnrichmentFunctions.TABLE_PROVIDER_TYPE_CONF, Provider.class.getName());
      config.put(SimpleHBaseEnrichmentFunctions.ACCESS_TRACKER_TYPE_CONF, "PERSISTENT_BLOOM");
      config.put(PersistentBloomTrackerCreator.Config.PERSISTENT_BLOOM_TABLE, trackerHBaseTableName);
      config.put(PersistentBloomTrackerCreator.Config.PERSISTENT_BLOOM_CF, cf);
      config.put(GeoLiteDatabase.GEO_HDFS_FILE, geoHdfsFile.getAbsolutePath());
      globalConfigStr = JSONUtils.INSTANCE.toJSON(config, true);
    }
    ConfigUploadComponent configUploadComponent = new ConfigUploadComponent()
            .withTopologyProperties(topologyProperties)
            .withGlobalConfig(globalConfigStr)
            .withEnrichmentConfigsPath(TestConstants.SAMPLE_CONFIG_PATH);

    //create MockHBaseTables
    final MockHTable trackerTable = (MockHTable) MockHTable.Provider.addToCache(trackerHBaseTableName, cf);
    final MockHTable threatIntelTable = (MockHTable) MockHTable.Provider.addToCache(threatIntelTableName, cf);
    EnrichmentHelper.INSTANCE.load(threatIntelTable, cf, new ArrayList<LookupKV<EnrichmentKey, EnrichmentValue>>() {{
      add(new LookupKV<>(new EnrichmentKey(MALICIOUS_IP_TYPE, "10.0.2.3"), new EnrichmentValue(new HashMap<>())));
    }});
    final MockHTable enrichmentTable = (MockHTable) MockHTable.Provider.addToCache(enrichmentsTableName, cf);
    EnrichmentHelper.INSTANCE.load(enrichmentTable, cf, new ArrayList<LookupKV<EnrichmentKey, EnrichmentValue>>() {{
      add(new LookupKV<>(new EnrichmentKey(PLAYFUL_CLASSIFICATION_TYPE, "10.0.2.3")
                      , new EnrichmentValue(PLAYFUL_ENRICHMENT)
              )
      );
    }});

    FluxTopologyComponent fluxComponent = new FluxTopologyComponent.Builder()
            .withTopologyLocation(new File(fluxPath))
            .withTopologyName("test")
            .withTopologyProperties(topologyProperties)
            .build();


    //UnitTestHelper.verboseLogging();
    ComponentRunner runner = new ComponentRunner.Builder()
            .withComponent("zk",zkServerComponent)
            .withComponent("kafka", kafkaComponent)
            .withComponent("config", configUploadComponent)
            .withComponent("storm", fluxComponent)
            .withMillisecondsBetweenAttempts(15000)
            .withCustomShutdownOrder(new String[]{"storm","config","kafka","zk"})
            .withNumRetries(10)
            .build();

    try {
      runner.start();
      fluxComponent.submitTopology();

      kafkaComponent.writeMessages(Constants.ENRICHMENT_TOPIC, inputMessages);
      ProcessorResult<Map<String, List<Map<String, Object>>>> result = runner.process(getProcessor());
      Map<String,List<Map<String, Object>>> outputMessages = result.getResult();
      List<Map<String, Object>> docs = outputMessages.get(Constants.INDEXING_TOPIC);
      Assert.assertEquals(inputMessages.size(), docs.size());
      validateAll(docs);
      List<Map<String, Object>> errors = outputMessages.get(ERROR_TOPIC);
      Assert.assertEquals(inputMessages.size(), errors.size());
      validateErrors(errors);
    } finally {
      runner.stop();
    }
  }

  public void dumpParsedMessages(List<Map<String,Object>> outputMessages, StringBuffer buffer) {
    for (Map<String,Object> map  : outputMessages) {
      for( String json : map.keySet()) {
        buffer.append(json).append("\n");
      }
    }
  }

  public static void validateAll(List<Map<String, Object>> docs) {

    for (Map<String, Object> doc : docs) {
      baseValidation(doc);
      hostEnrichmentValidation(doc);
      geoEnrichmentValidation(doc);
      threatIntelValidation(doc);
      simpleEnrichmentValidation(doc);
    }
  }

  protected void validateErrors(List<Map<String, Object>> errors) {
    for(Map<String, Object> error : errors) {
      Assert.assertEquals("Test throwing error from ErrorEnrichmentBolt", error.get(Constants.ErrorFields.MESSAGE.getName()));
      Assert.assertEquals("java.lang.IllegalStateException: Test throwing error from ErrorEnrichmentBolt", error.get(Constants.ErrorFields.EXCEPTION.getName()));
      Assert.assertEquals(Constants.ErrorType.ENRICHMENT_ERROR.getType(), error.get(Constants.ErrorFields.ERROR_TYPE.getName()));
      Assert.assertEquals("{\"rawMessage\":\"Error Test Raw Message String\"}", error.get(Constants.ErrorFields.RAW_MESSAGE.getName()));
    }
  }

  public static void baseValidation(Map<String, Object> jsonDoc) {
    assertEnrichmentsExists("threatintels.", setOf("hbaseThreatIntel"), jsonDoc.keySet());
    assertEnrichmentsExists("enrichments.", setOf("geo", "host", "hbaseEnrichment" ), jsonDoc.keySet());

    //ensure no values are empty
    for(Map.Entry<String, Object> kv : jsonDoc.entrySet()) {
      String actual = Objects.toString(kv.getValue(), "");
      Assert.assertTrue(String.format("Value of '%s' is empty: '%s'", kv.getKey(), actual), StringUtils.isNotEmpty(actual));
    }

    //ensure we always have a source ip and destination ip
    Assert.assertNotNull(jsonDoc.get(SRC_IP));
    Assert.assertNotNull(jsonDoc.get(DST_IP));

    Assert.assertNotNull(jsonDoc.get("ALL_CAPS"));
    Assert.assertNotNull(jsonDoc.get("foo"));
    Assert.assertEquals("TEST", jsonDoc.get("ALL_CAPS"));
    Assert.assertNotNull(jsonDoc.get("bar"));
    Assert.assertEquals("TEST", jsonDoc.get("bar"));
  }

  private static class EvaluationPayload {
    Map<String, Object> indexedDoc;
    String key;
    public EvaluationPayload(Map<String, Object> indexedDoc, String key) {
      this.indexedDoc = indexedDoc;
      this.key = key;
    }
  }

  private static enum HostEnrichments implements Predicate<EvaluationPayload>{

    LOCAL_LOCATION(new Predicate<EvaluationPayload>() {

      @Override
      public boolean apply(@Nullable EvaluationPayload evaluationPayload) {

        return evaluationPayload.indexedDoc.getOrDefault("enrichments.host." + evaluationPayload.key + ".known_info.local","").equals("YES");

      }
    })

    ,UNKNOWN_LOCATION(new Predicate<EvaluationPayload>() {

      @Override
      public boolean apply(@Nullable EvaluationPayload evaluationPayload) {
        return evaluationPayload.indexedDoc.getOrDefault("enrichments.host." + evaluationPayload.key + ".known_info.local","").equals("UNKNOWN");
      }
    })
    ,IMPORTANT(new Predicate<EvaluationPayload>() {
      @Override
      public boolean apply(@Nullable EvaluationPayload evaluationPayload) {
        return evaluationPayload.indexedDoc.getOrDefault("enrichments.host." + evaluationPayload.key + ".known_info.asset_value","").equals("important");
      }
    })
    ,PRINTER_TYPE(new Predicate<EvaluationPayload>() {
      @Override
      public boolean apply(@Nullable EvaluationPayload evaluationPayload) {
        return evaluationPayload.indexedDoc.getOrDefault("enrichments.host." + evaluationPayload.key + ".known_info.type","").equals("printer");
      }
    })
    ,WEBSERVER_TYPE(new Predicate<EvaluationPayload>() {
      @Override
      public boolean apply(@Nullable EvaluationPayload evaluationPayload) {
        return evaluationPayload.indexedDoc.getOrDefault("enrichments.host." + evaluationPayload.key + ".known_info.type","").equals("webserver");
      }
    })
    ,UNKNOWN_TYPE(new Predicate<EvaluationPayload>() {
      @Override
      public boolean apply(@Nullable EvaluationPayload evaluationPayload) {
        return evaluationPayload.indexedDoc.getOrDefault("enrichments.host." + evaluationPayload.key + ".known_info.type","").equals("unknown");
      }
    })
    ;

    Predicate<EvaluationPayload> _predicate;
    HostEnrichments(Predicate<EvaluationPayload> predicate) {
      this._predicate = predicate;
    }

    @Override
    public boolean apply(EvaluationPayload payload) {
      return _predicate.apply(payload);
    }

  }

  private static void assertEnrichmentsExists(String topLevel, Set<String> expectedEnrichments, Set<String> keys) {
    for(String key : keys) {
      if(key.startsWith(topLevel)) {
        String secondLevel = Iterables.get(Splitter.on(".").split(key), 1);
        String message = "Found an enrichment/threat intel (" + secondLevel + ") that I didn't expect (expected enrichments :"
                       + Joiner.on(",").join(expectedEnrichments) + "), but it was not there.  If you've created a new"
                       + " enrichment, then please add a validation method to this unit test.  Otherwise, it's a solid error"
                       + " and should be investigated.";
        Assert.assertTrue( message, expectedEnrichments.contains(secondLevel));
      }
    }
  }
  private static void simpleEnrichmentValidation(Map<String, Object> indexedDoc) {
    if(indexedDoc.getOrDefault(SRC_IP,"").equals("10.0.2.3")
            || indexedDoc.getOrDefault(DST_IP,"").equals("10.0.2.3")
            ) {
      Assert.assertTrue(keyPatternExists("enrichments.hbaseEnrichment", indexedDoc));
      if(indexedDoc.getOrDefault(SRC_IP,"").equals("10.0.2.3")) {
        Assert.assertEquals(indexedDoc.get("enrichments.hbaseEnrichment." + SRC_IP + "." + PLAYFUL_CLASSIFICATION_TYPE+ ".orientation")
                , PLAYFUL_ENRICHMENT.get("orientation")
        );
        Assert.assertEquals(indexedDoc.get("src_classification.orientation")
                , PLAYFUL_ENRICHMENT.get("orientation"));
        Assert.assertEquals(indexedDoc.get("is_src_malicious")
                , true);
      }
      else if(indexedDoc.getOrDefault(DST_IP,"").equals("10.0.2.3")) {
        Assert.assertEquals( indexedDoc.get("enrichments.hbaseEnrichment." + DST_IP + "." + PLAYFUL_CLASSIFICATION_TYPE + ".orientation")
                , PLAYFUL_ENRICHMENT.get("orientation")
        );
        Assert.assertEquals(indexedDoc.get("dst_classification.orientation")
                , PLAYFUL_ENRICHMENT.get("orientation"));

      }
      if(!indexedDoc.getOrDefault(SRC_IP,"").equals("10.0.2.3")) {
        Assert.assertEquals(indexedDoc.get("is_src_malicious")
                , false);
      }
    }
    else {
      Assert.assertEquals(indexedDoc.get("is_src_malicious")
              , false);
    }
  }
  private static void threatIntelValidation(Map<String, Object> indexedDoc) {
    if(indexedDoc.getOrDefault(SRC_IP,"").equals("10.0.2.3") ||
            indexedDoc.getOrDefault(DST_IP,"").equals("10.0.2.3")) {

      //if we have any threat intel messages, we want to tag is_alert to true
      Assert.assertTrue(keyPatternExists("threatintels.", indexedDoc));
      Assert.assertEquals(indexedDoc.getOrDefault("is_alert",""), "true");

      // validate threat triage score
      Assert.assertTrue(indexedDoc.containsKey(THREAT_TRIAGE_SCORE_KEY));
      Double score = (Double) indexedDoc.get(THREAT_TRIAGE_SCORE_KEY);
      Assert.assertEquals(score, 10d, 1e-7);

      // validate threat triage rules
      Joiner joiner = Joiner.on(".");
      Stream.of(
              joiner.join(THREAT_TRIAGE_RULES_KEY, 0, THREAT_TRIAGE_RULE_NAME),
              joiner.join(THREAT_TRIAGE_RULES_KEY, 0, THREAT_TRIAGE_RULE_COMMENT),
              joiner.join(THREAT_TRIAGE_RULES_KEY, 0, THREAT_TRIAGE_RULE_REASON),
              joiner.join(THREAT_TRIAGE_RULES_KEY, 0, THREAT_TRIAGE_RULE_SCORE))
              .forEach(key ->
                      Assert.assertTrue(String.format("Missing expected key: '%s'", key), indexedDoc.containsKey(key)));
    }
    else {
      //For YAF this is the case, but if we do snort later on, this will be invalid.
      Assert.assertNull(indexedDoc.get("is_alert"));
      Assert.assertFalse(keyPatternExists("threatintels.", indexedDoc));
    }

    //ip threat intels
    if(keyPatternExists("threatintels.hbaseThreatIntel.", indexedDoc)) {
      if(indexedDoc.getOrDefault(SRC_IP,"").equals("10.0.2.3")) {
        Assert.assertEquals(indexedDoc.get("threatintels.hbaseThreatIntel." + SRC_IP + "." + MALICIOUS_IP_TYPE), "alert");
      }
      else if(indexedDoc.getOrDefault(DST_IP,"").equals("10.0.2.3")) {
        Assert.assertEquals(indexedDoc.get("threatintels.hbaseThreatIntel." + DST_IP + "." + MALICIOUS_IP_TYPE), "alert");
      }
      else {
        Assert.fail("There was a threat intels that I did not expect: " + indexedDoc);
      }
    }

  }

  private static void geoEnrichmentValidation(Map<String, Object> indexedDoc) {
    // Need to check both separately. Local IPs will have no Geo entries
    if(indexedDoc.containsKey("enrichments.geo." + DST_IP + ".location_point")) {
      Assert.assertEquals(DEFAULT_LOCATION_POINT, indexedDoc.get("enrichments.geo." + DST_IP + ".location_point"));
      Assert.assertEquals(DEFAULT_LONGITUDE, indexedDoc.get("enrichments.geo." + DST_IP + ".longitude"));
      Assert.assertEquals(DEFAULT_CITY, indexedDoc.get("enrichments.geo." + DST_IP + ".city"));
      Assert.assertEquals(DEFAULT_LATITUDE, indexedDoc.get("enrichments.geo." + DST_IP + ".latitude"));
      Assert.assertEquals(DEFAULT_COUNTRY, indexedDoc.get("enrichments.geo." + DST_IP + ".country"));
      Assert.assertEquals(DEFAULT_DMACODE, indexedDoc.get("enrichments.geo." + DST_IP + ".dmaCode"));
      Assert.assertEquals(DEFAULT_POSTAL_CODE, indexedDoc.get("enrichments.geo." + DST_IP + ".postalCode"));
    }
    if(indexedDoc.containsKey("enrichments.geo." + SRC_IP + ".location_point")) {
      Assert.assertEquals(DEFAULT_LOCATION_POINT, indexedDoc.get("enrichments.geo." + SRC_IP + ".location_point"));
      Assert.assertEquals(DEFAULT_LONGITUDE, indexedDoc.get("enrichments.geo." + SRC_IP + ".longitude"));
      Assert.assertEquals(DEFAULT_CITY, indexedDoc.get("enrichments.geo." + SRC_IP + ".city"));
      Assert.assertEquals(DEFAULT_LATITUDE, indexedDoc.get("enrichments.geo." + SRC_IP + ".latitude"));
      Assert.assertEquals(DEFAULT_COUNTRY, indexedDoc.get("enrichments.geo." + SRC_IP + ".country"));
      Assert.assertEquals(DEFAULT_DMACODE, indexedDoc.get("enrichments.geo." + SRC_IP + ".dmaCode"));
      Assert.assertEquals(DEFAULT_POSTAL_CODE, indexedDoc.get("enrichments.geo." + SRC_IP + ".postalCode"));
    }
  }

  private static void hostEnrichmentValidation(Map<String, Object> indexedDoc) {
    boolean enriched = false;
    //important local printers
    {
      Set<String> ips = setOf("10.0.2.15", "10.60.10.254");
      if (ips.contains(indexedDoc.get(SRC_IP))) {
        //this is a local, important, printer
        Assert.assertTrue(Predicates.and(HostEnrichments.LOCAL_LOCATION
                ,HostEnrichments.IMPORTANT
                ,HostEnrichments.PRINTER_TYPE
                ).apply(new EvaluationPayload(indexedDoc, SRC_IP))
        );
        enriched = true;
      }
      if (ips.contains(indexedDoc.get(DST_IP))) {
        Assert.assertTrue(Predicates.and(HostEnrichments.LOCAL_LOCATION
                ,HostEnrichments.IMPORTANT
                ,HostEnrichments.PRINTER_TYPE
                ).apply(new EvaluationPayload(indexedDoc, DST_IP))
        );
        enriched = true;
      }
    }
    //important local webservers
    {
      Set<String> ips = setOf("10.1.128.236");
      if (ips.contains(indexedDoc.get(SRC_IP))) {
        //this is a local, important, printer
        Assert.assertTrue(Predicates.and(HostEnrichments.LOCAL_LOCATION
                ,HostEnrichments.IMPORTANT
                ,HostEnrichments.WEBSERVER_TYPE
                ).apply(new EvaluationPayload(indexedDoc, SRC_IP))
        );
        enriched = true;
      }
      if (ips.contains(indexedDoc.get(DST_IP))) {
        Assert.assertTrue(Predicates.and(HostEnrichments.LOCAL_LOCATION
                ,HostEnrichments.IMPORTANT
                ,HostEnrichments.WEBSERVER_TYPE
                ).apply(new EvaluationPayload(indexedDoc, DST_IP))
        );
        enriched = true;
      }
    }
    if(!enriched) {
      Assert.assertFalse(keyPatternExists("enrichments.host", indexedDoc));
    }
  }


  private static boolean keyPatternExists(String pattern, Map<String, Object> indexedObj) {
    for(String k : indexedObj.keySet()) {
      if(k.startsWith(pattern)) {
        return true;
      }
    }
    return false;
  }
  private static Set<String> setOf(String... items) {
    Set<String> ret = new HashSet<>();
    for(String item : items) {
      ret.add(item);
    }
    return ret;
  }

  private static List<Map<String, Object>> loadMessages(List<byte[]> outputMessages) {
    List<Map<String, Object>> tmp = new ArrayList<>();
    Iterables.addAll(tmp
            , Iterables.transform(outputMessages
                    , message -> {
                      try {
                        return new HashMap<>(JSONUtils.INSTANCE.load(new String(message)
                                , new TypeReference<Map<String, Object>>() {}
                        )
                        );
                      } catch (Exception ex) {
                        throw new IllegalStateException(ex);
                      }
                    }
            )
    );
    return tmp;
  }
  @SuppressWarnings("unchecked")
  private KafkaProcessor<Map<String,List<Map<String, Object>>>> getProcessor(){

    return new KafkaProcessor<>()
            .withKafkaComponentName("kafka")
            .withReadTopic(Constants.INDEXING_TOPIC)
            .withErrorTopic(ERROR_TOPIC)
            .withValidateReadMessages(new Function<KafkaMessageSet, Boolean>() {
              @Nullable
              @Override
              public Boolean apply(@Nullable KafkaMessageSet messageSet) {
                return (messageSet.getMessages().size() == inputMessages.size()) && (messageSet.getErrors().size() == inputMessages.size());
              }
            })
            .withProvideResult(new Function<KafkaMessageSet,Map<String,List<Map<String, Object>>>>(){
              @Nullable
              @Override
              public Map<String,List<Map<String, Object>>> apply(@Nullable KafkaMessageSet messageSet) {
                return new HashMap<String, List<Map<String, Object>>>() {{
                  put(Constants.INDEXING_TOPIC, loadMessages(messageSet.getMessages()));
                  put(ERROR_TOPIC, loadMessages(messageSet.getErrors()));
                }};
              }
            });
  }
}
