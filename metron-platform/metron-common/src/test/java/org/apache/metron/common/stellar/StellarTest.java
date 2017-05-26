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

package org.apache.metron.common.stellar;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.apache.commons.lang3.StringUtils;
import org.apache.metron.common.dsl.ParseException;
import org.apache.metron.common.dsl.Stellar;
import org.apache.metron.common.dsl.StellarFunction;
import org.apache.metron.common.dsl.StellarFunctions;
import org.apache.metron.common.dsl.functions.resolver.ClasspathFunctionResolver;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.reflections.Reflections;
import org.reflections.util.ConfigurationBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import static org.apache.metron.common.utils.StellarProcessorUtils.run;
import static org.apache.metron.common.utils.StellarProcessorUtils.runPredicate;

@SuppressWarnings("ALL")
public class StellarTest {

  @Test
  public void ensureDocumentation() {
    ClassLoader classLoader = getClass().getClassLoader();
    int numFound = 0;
    for (Class<?> clazz : new ClasspathFunctionResolver().resolvables()) {
      if (clazz.isAnnotationPresent(Stellar.class)) {
        numFound++;
        Stellar annotation = clazz.getAnnotation(Stellar.class);
        Assert.assertFalse("Must specify a name for " + clazz.getName(),StringUtils.isEmpty(annotation.name()));
        Assert.assertFalse("Must specify a description annotation for " + clazz.getName(),StringUtils.isEmpty(annotation.description()));
        Assert.assertTrue("Must specify a non-empty params for " + clazz.getName(), annotation.params().length > 0);
        Assert.assertTrue("Must specify a non-empty params for " + clazz.getName(), StringUtils.isNoneEmpty(annotation.params()));
        Assert.assertFalse("Must specify a returns annotation for " + clazz.getName(), StringUtils.isEmpty(annotation.returns()));
      }
    }
    Assert.assertTrue(numFound > 0);
  }

  @Test
  public void testEscapedLiterals() {
    Assert.assertEquals("'bar'", run("\"'bar'\"", new HashMap<>()));
    Assert.assertEquals("'BAR'", run("TO_UPPER('\\'bar\\'')", new HashMap<>()));
    Assert.assertEquals("\"bar\"", run("\"\\\"bar\\\"\"", new HashMap<>()));
    Assert.assertEquals("\"bar\"", run("'\"bar\"'", new HashMap<>()));
    Assert.assertEquals("\"BAR\"", run("TO_UPPER(\"\\\"bar\\\"\")", new HashMap<>()));
    Assert.assertEquals("bar \\ foo", run("'bar \\\\ foo'", new HashMap<>()));
    Assert.assertEquals("bar \\\\ foo", run("'bar \\\\\\\\ foo'", new HashMap<>()));
    Assert.assertEquals("bar\nfoo", run("'bar\\nfoo'", new HashMap<>()));
    Assert.assertEquals("bar\n\nfoo", run("'bar\\n\\nfoo'", new HashMap<>()));
    Assert.assertEquals("bar\tfoo", run("'bar\\tfoo'", new HashMap<>()));
    Assert.assertEquals("bar\t\tfoo", run("'bar\\t\\tfoo'", new HashMap<>()));
    Assert.assertEquals("bar\rfoo", run("'bar\\rfoo'", new HashMap<>()));
    Assert.assertEquals("'bar'", run("'\\'bar\\''", new HashMap<>()));
  }

  @Test
  public void testVariableResolution() {
    {
      String query = "bar:variable";
      Assert.assertEquals("bar", run(query, ImmutableMap.of("bar:variable", "bar")));
      Assert.assertEquals("grok", run(query, ImmutableMap.of("bar:variable", "grok")));
    }
    {
      String query = "JOIN(['foo', bar:variable], '')";
      Assert.assertEquals("foobar", run(query, ImmutableMap.of("bar:variable", "bar")));
      Assert.assertEquals("foogrok", run(query, ImmutableMap.of("bar:variable", "grok")));
    }
    {
      String query = "MAP_GET('bar', { 'foo' : 1, 'bar' : bar:variable})";
      Assert.assertEquals("bar", run(query, ImmutableMap.of("bar:variable", "bar")));
      Assert.assertEquals("grok", run(query, ImmutableMap.of("bar:variable", "grok")));
    }
  }

  @Test
  public void testIfThenElseBug1() {
    String query = "50 + (true == true ? 10 : 20)";
    Assert.assertEquals(60, run(query, new HashMap<>()));
  }

  @Test
  public void testIfThenElseBug2() {
    String query = "50 + (true == false ? 10 : 20)";
    Assert.assertEquals(70, run(query, new HashMap<>()));
  }

  @Test
  public void testIfThenElseBug3() {
    String query = "50 * (true == false ? 2 : 10) + 20";
    Assert.assertEquals(520, run(query, new HashMap<>()));
  }

  @Test
  public void testIfThenElseBug4() {
    String query = "TO_INTEGER(true == true ? 10.0 : 20.0 )";
    Assert.assertEquals(10, run(query, new HashMap<>()));
  }

  @Test
  public void testVariablesUsed() {
    StellarProcessor processor = new StellarProcessor();
    {
      Assert.assertEquals(new HashSet<>(), processor.variablesUsed("if 1 < 2 then 'one' else 'two'"));
    }
    {
      Assert.assertEquals(ImmutableSet.of("one")
                         , processor.variablesUsed("if 1 < 2 then one else 'two'"));
    }
    {
      Assert.assertEquals(ImmutableSet.of("one", "two")
                         , processor.variablesUsed("if 1 < 2 then one else two"));
    }
  }

  @Test
  public void testFunctionEmptyArgs() {
    {
      String query = "STARTS_WITH(casey, 'case') or MAP_EXISTS()";
      Assert.assertTrue((Boolean)run(query, ImmutableMap.of("casey", "casey")));
    }
    {
      String query = "true or MAP_EXISTS()";
      Assert.assertTrue((Boolean)run(query, new HashMap<>()));
    }
    {
      String query = "MAP_EXISTS() or true";
      Assert.assertTrue((Boolean)run(query, new HashMap<>()));
    }
  }
  @Test
  public void testNull() {
    {
      String query = "if 1 < 2 then NULL else true";
      Assert.assertNull(run(query, new HashMap<>()));
    }
    {
      String query = "1 < 2 ? NULL : true";
      Assert.assertNull(run(query, new HashMap<>()));
    }
    {
      String query = "null == null ? true : false";
      Assert.assertTrue((Boolean)run(query, new HashMap<>()));
    }
  }

  @Test
  public void testMapConstant() {
    {
      String query = "MAP_GET('bar', { 'foo' : 1, 'bar' : 'bar'})";
      Assert.assertEquals("bar", run(query, new HashMap<>()));
    }
    {
      String query = "MAP_GET('blah', {  'blah' : 1 < 2 })";
      Assert.assertEquals(true, run(query, new HashMap<>()));
    }
    {
      String query = "MAP_GET('blah', {  'blah' : not(STARTS_WITH(casey, 'case')) })";
      Assert.assertEquals(false, run(query, ImmutableMap.of("casey", "casey")));
    }
    {
      String query = "MAP_GET('blah', {  'blah' : one })";
      Assert.assertEquals(1, run(query, ImmutableMap.of("one", 1)));
    }
    {
      String query = "MAP_GET('blah', {  'blah' : null })";
      Assert.assertNull(run(query, new HashMap<>()));
    }
    {
      String query = "MAP_GET('BLAH', {  TO_UPPER('blah') : null })";
      Assert.assertNull(run(query, new HashMap<>()));
    }
    {
      String query = "MAP_GET('BLAH', {  TO_UPPER('blah') : 1 < 2 })";
      Assert.assertEquals(true, run(query, new HashMap<>()));
    }
  }

  @Test
  public void testIfThenElse() {
    {
      String query = "if STARTS_WITH(casey, 'case') then 'one' else 'two'";
      Assert.assertEquals("one", run(query, ImmutableMap.of("casey", "casey")));
    }
    {
      String query = "if 1 < 2 then 'one' else 'two'";
      Assert.assertEquals("one", run(query, new HashMap<>()));
    }
    {
      String query = "if 1 + 1 < 2 then 'one' else 'two'";
      Assert.assertEquals("two", run(query, new HashMap<>()));
    }
    {
      String query = "if 1 + 1 <= 2 AND 1 + 2 in [3] then 'one' else 'two'";
      Assert.assertEquals("one", run(query, new HashMap<>()));
    }
    {
      String query = "if 1 + 1 <= 2 AND (1 + 2 in [3]) then 'one' else 'two'";
      Assert.assertEquals("one", run(query, new HashMap<>()));
    }
    {
      String query = "if not(1 < 2) then 'one' else 'two'";
      Assert.assertEquals("two", run(query, new HashMap<>()));
    }
    {
      String query = "if 1 == 1.0000001 then 'one' else 'two'";
      Assert.assertEquals("two", run(query, new HashMap<>()));
    }
    {
      String query = "if one < two then 'one' else 'two'";
      Assert.assertEquals("one", run(query, ImmutableMap.of("one", 1, "two", 2)));
    }
    {
      String query = "if one == very_nearly_one then 'one' else 'two'";
      Assert.assertEquals("two", run(query, ImmutableMap.of("one", 1, "very_nearly_one", 1.0000001)));
    }
    {
      String query = "if one == very_nearly_one OR one == very_nearly_one then 'one' else 'two'";
      Assert.assertEquals("two", run(query, ImmutableMap.of("one", 1, "very_nearly_one", 1.0000001)));
    }
    {
      String query = "if one == very_nearly_one OR one != very_nearly_one then 'one' else 'two'";
      Assert.assertEquals("one", run(query, ImmutableMap.of("one", 1, "very_nearly_one", 1.0000001)));
    }
    {
      String query = "if one != very_nearly_one OR one == very_nearly_one then 'one' else 'two'";
      Assert.assertEquals("one", run(query, ImmutableMap.of("one", 1, "very_nearly_one", 1.0000001)));
    }
    {
      String query = "if 'foo' in ['foo'] OR one == very_nearly_one then 'one' else 'two'";
      Assert.assertEquals("one", run(query, ImmutableMap.of("one", 1, "very_nearly_one", 1.0000001)));
    }
    {
      String query = "if ('foo' in ['foo']) OR one == very_nearly_one then 'one' else 'two'";
      Assert.assertEquals("one", run(query, ImmutableMap.of("one", 1, "very_nearly_one", 1.0000001)));
    }
    {
      String query = "if not('foo' in ['foo']) OR one == very_nearly_one then 'one' else 'two'";
      Assert.assertEquals("two", run(query, ImmutableMap.of("one", 1, "very_nearly_one", 1.0000001)));
    }
    {
      String query = "if not('foo' in ['foo'] OR one == very_nearly_one) then 'one' else 'two'";
      Assert.assertEquals("two", run(query, ImmutableMap.of("one", 1, "very_nearly_one", 1.0000001)));
    }
    {
      String query = "1 < 2 ? 'one' : 'two'";
      Assert.assertEquals("one", run(query, new HashMap<>()));
    }
    {
      String query = "1 < 2 ? TO_UPPER('one') : 'two'";
      Assert.assertEquals("ONE", run(query, new HashMap<>()));
    }
    {
      String query = "1 < 2 ? one : 'two'";
      Assert.assertEquals("one", run(query, ImmutableMap.of("one", "one")));
    }
    {
      String query = "1 < 2 ? one*3 : 'two'";
      Assert.assertTrue(Math.abs(3 - (int) run(query, ImmutableMap.of("one", 1))) < 1e-6);
    }
    {
      String query = "1 < 2 AND 1 < 2 ? one*3 : 'two'";
      Assert.assertTrue(Math.abs(3 - (int) run(query, ImmutableMap.of("one", 1))) < 1e-6);
    }
    {
      String query = "1 < 2 AND 1 > 2 ? one*3 : 'two'";
      Assert.assertEquals("two", run(query, ImmutableMap.of("one", 1)));
    }
    {
      String query = "1 > 2 AND 1 < 2 ? one*3 : 'two'";
      Assert.assertEquals("two", run(query, ImmutableMap.of("one", 1)));
    }
    {
      String query = "1 < 2 AND 'foo' in ['', 'foo'] ? one*3 : 'two'";
      Assert.assertEquals(3, run(query, ImmutableMap.of("one", 1)));
    }
    {
      String query = "1 < 2 AND ('foo' in ['', 'foo']) ? one*3 : 'two'";
      Assert.assertEquals(3, run(query, ImmutableMap.of("one", 1)));
    }
    {
      String query = "'foo' in ['', 'foo'] ? one*3 : 'two'";
      Assert.assertEquals(3, run(query, ImmutableMap.of("one", 1)));
    }
  }

  @Test
  public void testInNotIN(){
    HashMap variables = new HashMap<>();
    boolean thrown = false;
    try{
      Object o = run("in in ['','in']" ,variables );
    }catch(ParseException pe) {
      thrown = true;
    }
    Assert.assertTrue(thrown);
    thrown = false;

    try{
      Assert.assertEquals(true,run("'in' in ['','in']" ,variables ));
    }catch(ParseException pe) {
      thrown = true;
    }
    Assert.assertFalse(thrown);
  }

  @Test
  public void testHappyPath() {
    String query = "TO_UPPER(TRIM(foo))";
    Assert.assertEquals("CASEY", run(query, ImmutableMap.of("foo", "casey ")));
  }

  @Test
  public void testLengthString(){
    String query = "LENGTH(foo)";
    Assert.assertEquals(5, run(query,ImmutableMap.of("foo","abcde")));
  }
  @Test
  public void testLengthCollection(){
    String query = "LENGTH(foo)";
    Collection c = Arrays.asList(1,2,3,4,5);
    Assert.assertEquals(5, run(query,ImmutableMap.of("foo",c)));
  }

  @Test
  public void testEmptyLengthString(){
    String query = "LENGTH(foo)";
    Assert.assertEquals(0,run(query,ImmutableMap.of("foo","")));
  }
  @Test
  public void testEmptyLengthCollection(){
    String query = "LENGTH(foo)";
    Collection c = new ArrayList();
    Assert.assertEquals(0,run(query,ImmutableMap.of("foo",c)));
  }
  @Test
  public void testNoVarLength(){
    String query = "LENGTH(foo)";
    Assert.assertEquals(0,run(query,ImmutableMap.of()));
  }

  @Test
  public void testJoin() {
    String query = "JOIN( [ TO_UPPER(TRIM(foo)), 'bar' ], ',')";
    Assert.assertEquals("CASEY,bar", run(query, ImmutableMap.of("foo", "casey ")));
  }

  @Test
  public void testSplit() {
    String query = "JOIN( SPLIT(foo, ':'), ',')";
    Assert.assertEquals("casey,bar", run(query, ImmutableMap.of("foo", "casey:bar")));
  }

  @Test
  public void testMapGet() {
    String query = "MAP_GET(dc, dc2tz, 'UTC')";
    Assert.assertEquals("UTC"
                       , run(query, ImmutableMap.of("dc", "nyc"
                                                   ,"dc2tz", ImmutableMap.of("la", "PST")
                                                   )
                            )
                       );
    Assert.assertEquals("EST"
                       , run(query, ImmutableMap.of("dc", "nyc"
                                                   ,"dc2tz", ImmutableMap.of("nyc", "EST")
                                                   )
                            )
                       );
  }

  @Test
  public void testTLDExtraction() {
    String query = "DOMAIN_TO_TLD(foo)";
    Assert.assertEquals("co.uk", run(query, ImmutableMap.of("foo", "www.google.co.uk")));
  }

  @Test
  public void testTLDRemoval() {
    String query = "DOMAIN_REMOVE_TLD(foo)";
    Assert.assertEquals("www.google", run(query, ImmutableMap.of("foo", "www.google.co.uk")));
  }

  @Test
  public void testSubdomainRemoval() {
    String query = "DOMAIN_REMOVE_SUBDOMAINS(foo)";
    Assert.assertEquals("google.co.uk", run(query, ImmutableMap.of("foo", "www.google.co.uk")));
    Assert.assertEquals("google.com", run(query, ImmutableMap.of("foo", "www.google.com")));
  }

  @Test
  public void testURLToHost() {
    String query = "URL_TO_HOST(foo)";
    Assert.assertEquals("www.google.co.uk", run(query, ImmutableMap.of("foo", "http://www.google.co.uk/my/path")));
  }

  @Test
  public void testURLToPort() {
    String query = "URL_TO_PORT(foo)";
    Assert.assertEquals(80, run(query, ImmutableMap.of("foo", "http://www.google.co.uk/my/path")));
  }

  @Test
  public void testURLToProtocol() {
    String query = "URL_TO_PROTOCOL(foo)";
    Assert.assertEquals("http", run(query, ImmutableMap.of("foo", "http://www.google.co.uk/my/path")));
  }

  @Test
  public void testURLToPath() {
    String query = "URL_TO_PATH(foo)";
    Assert.assertEquals("/my/path", run(query, ImmutableMap.of("foo", "http://www.google.co.uk/my/path")));
  }

  @Test
  public void testProtocolToName() {
    String query = "PROTOCOL_TO_NAME(protocol)";
    Assert.assertEquals("TCP", run(query, ImmutableMap.of("protocol", "6")));
    Assert.assertEquals("TCP", run(query, ImmutableMap.of("protocol", 6)));
    Assert.assertEquals(null, run(query, ImmutableMap.of("foo", 6)));
    Assert.assertEquals("chicken", run(query, ImmutableMap.of("protocol", "chicken")));
  }

  @Test
  public void testDateConversion() {
    long expected =1452013350000L;
    {
      String query = "TO_EPOCH_TIMESTAMP(foo, 'yyyy-MM-dd HH:mm:ss', 'UTC')";
      Assert.assertEquals(expected, run(query, ImmutableMap.of("foo", "2016-01-05 17:02:30")));
    }
    {
      String query = "TO_EPOCH_TIMESTAMP(foo, 'yyyy-MM-dd HH:mm:ss')";
      Long ts = (Long) run(query, ImmutableMap.of("foo", "2016-01-05 17:02:30"));
      //is it within 24 hours of the UTC?
      Assert.assertTrue(Math.abs(ts - expected) < 8.64e+7);
    }
  }

  @Test
  public void testToString() {
    Assert.assertEquals("5", run("TO_STRING(foo)", ImmutableMap.of("foo", 5)));
  }

  @Test
  public void testToInteger() {
    Assert.assertEquals(5, run("TO_INTEGER(foo)", ImmutableMap.of("foo", "5")));
    Assert.assertEquals(5, run("TO_INTEGER(foo)", ImmutableMap.of("foo", 5)));
  }

  @Test
  public void testToDouble() {
    Assert.assertEquals(5.1d, run("TO_DOUBLE(foo)", ImmutableMap.of("foo", 5.1d)));
    Assert.assertEquals(5.1d, run("TO_DOUBLE(foo)", ImmutableMap.of("foo", "5.1")));
  }

  @Test
  public void testGet() {
    Map<String, Object> variables = ImmutableMap.of("foo", "www.google.co.uk");
    Assert.assertEquals("www", run("GET_FIRST(SPLIT(DOMAIN_REMOVE_TLD(foo), '.'))", variables));
    Assert.assertEquals("www", run("GET(SPLIT(DOMAIN_REMOVE_TLD(foo), '.'), 0)", variables));
    Assert.assertEquals("google", run("GET_LAST(SPLIT(DOMAIN_REMOVE_TLD(foo), '.'))", variables));
    Assert.assertEquals("google", run("GET(SPLIT(DOMAIN_REMOVE_TLD(foo), '.'), 1)", variables));
  }

  @Test
  public void testBooleanOps() throws Exception {
    final Map<String, String> variableMap = new HashMap<String, String>() {{
      put("foo", "casey");
      put("empty", "");
      put("spaced", "metron is great");
    }};
    Assert.assertFalse(runPredicate("not('casey' == foo and true)", v -> variableMap.get(v)));
    Assert.assertTrue(runPredicate("not(not('casey' == foo and true))", v -> variableMap.get(v)));
    Assert.assertTrue(runPredicate("('casey' == foo) && ( false != true )", v -> variableMap.get(v)));
    Assert.assertFalse(runPredicate("('casey' == foo) and (FALSE == TRUE)", v -> variableMap.get(v)));
    Assert.assertFalse(runPredicate("'casey' == foo and FALSE", v -> variableMap.get(v)));
    Assert.assertTrue(runPredicate("'casey' == foo and true", v -> variableMap.get(v)));
    Assert.assertTrue(runPredicate("true", v -> variableMap.get(v)));
    Assert.assertTrue(runPredicate("TRUE", v -> variableMap.get(v)));
  }

  @Test
  public void testInCollection() throws Exception {
    final Map<String, String> variableMap = new HashMap<String, String>() {{
      put("foo", "casey");
      put("empty", "");
    }};
    Assert.assertTrue(runPredicate("foo in [ 'casey', 'david' ]", v -> variableMap.get(v)));
    Assert.assertFalse(runPredicate("foo in [ ]", v -> variableMap.get(v)));
    Assert.assertTrue(runPredicate("foo in [ foo, 'david' ]", v -> variableMap.get(v)));
    Assert.assertTrue(runPredicate("foo in [ 'casey', 'david' ] and 'casey' == foo", v -> variableMap.get(v)));
    Assert.assertTrue(runPredicate("foo in [ 'casey', 'david' ] and foo == 'casey'", v -> variableMap.get(v)));
    Assert.assertTrue(runPredicate("foo in [ 'casey' ]", v -> variableMap.get(v)));
    Assert.assertFalse(runPredicate("foo not in [ 'casey', 'david' ]", v -> variableMap.get(v)));
    Assert.assertFalse(runPredicate("foo not in [ 'casey', 'david' ] and 'casey' == foo", v -> variableMap.get(v)));
    Assert.assertTrue(runPredicate("null in [ null, 'something' ]", v -> variableMap.get(v)));
    Assert.assertFalse(runPredicate("null not in [ null, 'something' ]", v -> variableMap.get(v)));
  }

  @Test
  public void testInMap() throws Exception {
    final Map<String, String> variableMap = new HashMap<String, String>() {{
      put("foo", "casey");
      put("empty", "");
    }};
    Assert.assertTrue(runPredicate("'casey' in { foo : 5 }", v -> variableMap.get(v)));
    Assert.assertFalse(runPredicate("'casey' not in { foo : 5 }", v -> variableMap.get(v)));
    Assert.assertTrue(runPredicate("foo in { foo : 5 }", v -> variableMap.get(v)));
    Assert.assertFalse(runPredicate("foo not in { foo : 5 }", v -> variableMap.get(v)));
    Assert.assertTrue(runPredicate("'foo' in { 'foo' : 5 }", v -> variableMap.get(v)));
    Assert.assertFalse(runPredicate("'foo' not in { 'foo' : 5 }", v -> variableMap.get(v)));
    Assert.assertTrue(runPredicate("foo in { 'casey' : 5 }", v -> variableMap.get(v)));
    Assert.assertFalse(runPredicate("foo not in { 'casey' : 5 }", v -> variableMap.get(v)));
    Assert.assertFalse(runPredicate("empty in { foo : 5 }", v -> variableMap.get(v)));
    Assert.assertTrue(runPredicate("empty not in { foo : 5 }", v -> variableMap.get(v)));
    Assert.assertFalse(runPredicate("'foo' in { }", v -> variableMap.get(v)));
    Assert.assertFalse(runPredicate("null in { 'foo' : 5 }", v -> variableMap.get(v)));
    Assert.assertTrue(runPredicate("null not in { 'foo' : 5 }", v -> variableMap.get(v)));
  }

  @Test
  public void testInString() throws Exception {
    final Map<String, String> variableMap = new HashMap<String, String>() {{
      put("foo", "casey");
      put("empty", "");
    }};
    Assert.assertTrue(runPredicate("'case' in foo", v -> variableMap.get(v)));
    Assert.assertFalse(runPredicate("'case' not in foo", v -> variableMap.get(v)));
    Assert.assertFalse(runPredicate("'case' in empty", v -> variableMap.get(v)));
    Assert.assertTrue(runPredicate("'case' not in empty", v -> variableMap.get(v)));
    Assert.assertFalse(runPredicate("'case' in [ foo ]", v -> variableMap.get(v)));
    Assert.assertTrue(runPredicate("'case' not in [ foo ]", v -> variableMap.get(v)));
    Assert.assertFalse(runPredicate("null in foo", v -> variableMap.get(v)));
    Assert.assertTrue(runPredicate("null not in foo", v -> variableMap.get(v)));
  }

  @Test
  public void inNestedInStatement() throws Exception {
    final Map<String, String> variableMap = new HashMap<>();

    Assert.assertTrue(runPredicate("('grok' not in 'foobar') == true", variableMap::get));
    Assert.assertTrue(runPredicate("'grok' not in ('foobar' == true)", variableMap::get));
    Assert.assertFalse(runPredicate("'grok' in 'grokbar' == true", variableMap::get));
    Assert.assertTrue(runPredicate("false in 'grokbar' == true", variableMap::get));

    Assert.assertTrue(runPredicate("('foo' in 'foobar') == true", variableMap::get));
    Assert.assertFalse(runPredicate("'foo' in ('foobar' == true)", variableMap::get));
    Assert.assertTrue(runPredicate("'grok' not in 'grokbar' == true", variableMap::get));
    Assert.assertTrue(runPredicate("false in 'grokbar' == true", variableMap::get));
    Assert.assertTrue(runPredicate("'foo' in ['foo'] AND 'bar' in ['bar']", variableMap::get));
    Assert.assertTrue(runPredicate("('foo' in ['foo']) AND 'bar' in ['bar']", variableMap::get));
    Assert.assertTrue(runPredicate("'foo' in ['foo'] AND ('bar' in ['bar'])", variableMap::get));
    Assert.assertTrue(runPredicate("('foo' in ['foo']) AND ('bar' in ['bar'])", variableMap::get));
    Assert.assertTrue(runPredicate("('foo' in ['foo'] AND 'bar' in ['bar'])", variableMap::get));
  }

  @Test
  public void testExists() throws Exception {
    final Map<String, String> variableMap = new HashMap<String, String>() {{
      put("foo", "casey");
      put("empty", "");
      put("spaced", "metron is great");
    }};
    Assert.assertTrue(runPredicate("exists(foo)", v -> variableMap.get(v)));
    Assert.assertFalse(runPredicate("exists(bar)", v -> variableMap.get(v)));
    Assert.assertTrue(runPredicate("exists(bar) or true", v -> variableMap.get(v)));
  }

  @Test
  public void testMapFunctions_advanced() throws Exception {
    final Map<String, Object> variableMap = new HashMap<String, Object>() {{
      put("foo", "casey");
      put("bar", "bar.casey.grok");
      put("ip", "192.168.0.1");
      put("empty", "");
      put("spaced", "metron is great");
      put("myMap", ImmutableMap.of("casey", "apple"));
    }};
    Assert.assertTrue(runPredicate("MAP_EXISTS(foo, myMap)", v -> variableMap.get(v)));
  }

  @Test
  public void testLogicalFunctions() throws Exception {
    final Map<String, String> variableMap = new HashMap<String, String>() {{
      put("foo", "casey");
      put("ip", "192.168.0.1");
      put("ip_src_addr", "192.168.0.1");
      put("ip_dst_addr", "10.0.0.1");
      put("other_ip", "10.168.0.1");
      put("empty", "");
      put("spaced", "metron is great");
    }};
    Assert.assertTrue(runPredicate("IN_SUBNET(ip, '192.168.0.0/24')", v -> variableMap.get(v)));
    Assert.assertTrue(runPredicate("IN_SUBNET(ip, '192.168.0.0/24', '11.0.0.0/24')", v -> variableMap.get(v)));
    Assert.assertTrue(runPredicate("IN_SUBNET(ip, '192.168.0.0/24', '11.0.0.0/24') in [true]", v -> variableMap.get(v)));
    Assert.assertTrue(runPredicate("true in IN_SUBNET(ip, '192.168.0.0/24', '11.0.0.0/24')", v -> variableMap.get(v)));
    Assert.assertFalse(runPredicate("IN_SUBNET(ip_dst_addr, '192.168.0.0/24', '11.0.0.0/24')", v -> variableMap.get(v)));
    Assert.assertFalse(runPredicate("IN_SUBNET(other_ip, '192.168.0.0/24')", v -> variableMap.get(v)));
    Assert.assertFalse(runPredicate("IN_SUBNET(blah, '192.168.0.0/24')", v -> variableMap.get(v)));
    Assert.assertTrue(runPredicate("true and STARTS_WITH(foo, 'ca')", v -> variableMap.get(v)));
    Assert.assertTrue(runPredicate("true and STARTS_WITH(TO_UPPER(foo), 'CA')", v -> variableMap.get(v)));
    Assert.assertTrue(runPredicate("(true and STARTS_WITH(TO_UPPER(foo), 'CA')) || true", v -> variableMap.get(v)));
    Assert.assertTrue(runPredicate("true and ENDS_WITH(foo, 'sey')", v -> variableMap.get(v)));
    Assert.assertTrue(runPredicate("not(IN_SUBNET(ip_src_addr, '192.168.0.0/24') and IN_SUBNET(ip_dst_addr, '192.168.0.0/24'))", v-> variableMap.get(v)));
    Assert.assertTrue(runPredicate("IN_SUBNET(ip_src_addr, '192.168.0.0/24')", v-> variableMap.get(v)));
    Assert.assertFalse(runPredicate("not(IN_SUBNET(ip_src_addr, '192.168.0.0/24'))", v-> variableMap.get(v)));
    Assert.assertFalse(runPredicate("IN_SUBNET(ip_dst_addr, '192.168.0.0/24')", v-> variableMap.get(v)));
    Assert.assertTrue(runPredicate("not(IN_SUBNET(ip_dst_addr, '192.168.0.0/24'))", v-> variableMap.get(v)));
  }

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Test
  public void non_boolean_predicate_throws_exception() {
    final Map<String, String> variableMap = new HashMap<String, String>() {{
      put("protocol", "http");
    }};
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("The rule 'TO_UPPER(protocol)' does not return a boolean value.");
    runPredicate("TO_UPPER(protocol)", v -> variableMap.get(v));
  }
}
