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
package org.apache.metron.elasticsearch.writer;

import org.apache.storm.task.TopologyContext;
import org.apache.storm.tuple.Tuple;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import org.apache.metron.common.configuration.writer.WriterConfiguration;
import org.apache.metron.common.writer.BulkMessageWriter;
import org.apache.metron.common.writer.BulkWriterResponse;
import org.apache.metron.common.interfaces.FieldNameConverter;
import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.*;

public class ElasticsearchWriter implements BulkMessageWriter<JSONObject>, Serializable {

  private Map<String, String> optionalSettings;
  private transient TransportClient client;
  private SimpleDateFormat dateFormat;
  private static final Logger LOG = LoggerFactory
          .getLogger(ElasticsearchWriter.class);
  private FieldNameConverter fieldNameConverter = new ElasticsearchFieldNameConverter();

  public ElasticsearchWriter withOptionalSettings(Map<String, String> optionalSettings) {
    this.optionalSettings = optionalSettings;
    return this;
  }

  @Override
  public void init(Map stormConf, TopologyContext topologyContext, WriterConfiguration configurations) {
    Map<String, Object> globalConfiguration = configurations.getGlobalConfig();

    Settings.Builder settingsBuilder = Settings.settingsBuilder();
    settingsBuilder.put("cluster.name", globalConfiguration.get("es.clustername"));
    settingsBuilder.put("client.transport.ping_timeout","500s");

    if (optionalSettings != null) {

      settingsBuilder.put(optionalSettings);

    }

    Settings settings = settingsBuilder.build();

    try{
      client = TransportClient.builder().settings(settings).build();
      for(HostnamePort hp : getIps(globalConfiguration)) {
        client.addTransportAddress(
                new InetSocketTransportAddress(InetAddress.getByName(hp.hostname), hp.port)
        );
      }


    } catch (UnknownHostException exception){

      throw new RuntimeException(exception);
    }

    dateFormat = new SimpleDateFormat((String) globalConfiguration.get("es.date.format"));

  }

  public static class HostnamePort {
    String hostname;
    Integer port;
    public HostnamePort(String hostname, Integer port) {
      this.hostname = hostname;
      this.port = port;
    }
  }

  List<HostnamePort> getIps(Map<String, Object> globalConfiguration) {
    Object ipObj = globalConfiguration.get("es.ip");
    Object portObj = globalConfiguration.get("es.port");
    if(ipObj == null) {
      return Collections.emptyList();
    }
    if(ipObj instanceof String
            && ipObj.toString().contains(",") && ipObj.toString().contains(":")){
      List<String> ips = Arrays.asList(((String)ipObj).split(","));
      List<HostnamePort> ret = new ArrayList<>();
      for(String ip : ips) {
        Iterable<String> tokens = Splitter.on(":").split(ip);
        String host = Iterables.getFirst(tokens, null);
        String portStr = Iterables.getLast(tokens, null);
        ret.add(new HostnamePort(host, Integer.parseInt(portStr)));
      }
      return ret;
    }else if(ipObj instanceof String
            && ipObj.toString().contains(",")){
      List<String> ips = Arrays.asList(((String)ipObj).split(","));
      List<HostnamePort> ret = new ArrayList<>();
      for(String ip : ips) {
        ret.add(new HostnamePort(ip, Integer.parseInt(portObj + "")));
      }
      return ret;
    }else if(ipObj instanceof String
    && !ipObj.toString().contains(":")
      ) {
      return ImmutableList.of(new HostnamePort(ipObj.toString(), Integer.parseInt(portObj + "")));
    }
    else if(ipObj instanceof String
        && ipObj.toString().contains(":")
           ) {
      Iterable<String> tokens = Splitter.on(":").split(ipObj.toString());
      String host = Iterables.getFirst(tokens, null);
      String portStr = Iterables.getLast(tokens, null);
      return ImmutableList.of(new HostnamePort(host, Integer.parseInt(portStr)));
    }
    else if(ipObj instanceof List) {
      List<String> ips = (List)ipObj;
      List<HostnamePort> ret = new ArrayList<>();
      for(String ip : ips) {
        Iterable<String> tokens = Splitter.on(":").split(ip);
        String host = Iterables.getFirst(tokens, null);
        String portStr = Iterables.getLast(tokens, null);
        ret.add(new HostnamePort(host, Integer.parseInt(portStr)));
      }
      return ret;
    }
    throw new IllegalStateException("Unable to read the elasticsearch ips, expected es.ip to be either a list of strings, a string hostname or a host:port string");
  }

  @Override
  public BulkWriterResponse write(String sensorType, WriterConfiguration configurations, Iterable<Tuple> tuples, List<JSONObject> messages) throws Exception {
    String indexPostfix = dateFormat.format(new Date());
    BulkRequestBuilder bulkRequest = client.prepareBulk();

    for(JSONObject message: messages) {

      String indexName = sensorType;

      if (configurations != null) {
        indexName = configurations.getIndex(sensorType);
      }

      indexName = indexName + "_index_" + indexPostfix;

      JSONObject esDoc = new JSONObject();
      for(Object k : message.keySet()){

        deDot(k.toString(),message,esDoc);

      }

      IndexRequestBuilder indexRequestBuilder = client.prepareIndex(indexName,
              sensorType + "_doc");

      indexRequestBuilder = indexRequestBuilder.setSource(esDoc.toJSONString());
      Object ts = esDoc.get("timestamp");
      if(ts != null) {
        indexRequestBuilder = indexRequestBuilder.setTimestamp(ts.toString());
      }
      bulkRequest.add(indexRequestBuilder);

    }

    BulkResponse bulkResponse = bulkRequest.execute().actionGet();

    return buildWriteReponse(tuples, bulkResponse);
  }

  @Override
  public String getName() {
    return "elasticsearch";
  }

  protected BulkWriterResponse buildWriteReponse(Iterable<Tuple> tuples, BulkResponse bulkResponse) throws Exception {
    // Elasticsearch responses are in the same order as the request, giving us an implicit mapping with Tuples
    BulkWriterResponse writerResponse = new BulkWriterResponse();
    if (bulkResponse.hasFailures()) {
      Iterator<BulkItemResponse> respIter = bulkResponse.iterator();
      Iterator<Tuple> tupleIter = tuples.iterator();
      while (respIter.hasNext() && tupleIter.hasNext()) {
        BulkItemResponse item = respIter.next();
        Tuple tuple = tupleIter.next();

        if (item.isFailed()) {
          writerResponse.addError(item.getFailure().getCause(), tuple);
        } else {
          writerResponse.addSuccess(tuple);
        }

        // Should never happen, so fail the entire batch if it does.
        if (respIter.hasNext() != tupleIter.hasNext()) {
          throw new Exception(bulkResponse.buildFailureMessage());
        }
      }
    } else {
      writerResponse.addAllSuccesses(tuples);
    }

    return writerResponse;
  }

  @Override
  public void close() throws Exception {
    client.close();
  }

  //JSONObject doesn't expose map generics
  @SuppressWarnings("unchecked")
  private void deDot(String field, JSONObject origMessage, JSONObject message){

    if(field.contains(".")){

      if(LOG.isDebugEnabled()){
        LOG.debug("Dotted field: " + field);
      }

    }
    String newkey = fieldNameConverter.convert(field);
    message.put(newkey,origMessage.get(field));

  }

}

