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

import org.apache.metron.common.dsl.Context;
import org.apache.metron.common.dsl.Token;
import org.apache.metron.common.dsl.VariableResolver;
import org.apache.metron.common.dsl.functions.resolver.FunctionResolver;
import org.apache.metron.common.stellar.evaluators.ArithmeticEvaluator;
import org.apache.metron.common.stellar.evaluators.ComparisonExpressionWithOperatorEvaluator;
import org.apache.metron.common.stellar.evaluators.NumberLiteralEvaluator;
import org.apache.metron.common.stellar.generated.StellarParser;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.verification.VerificationMode;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Stack;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.*;


@RunWith(PowerMockRunner.class)
@PrepareForTest({Deque.class, ArithmeticEvaluator.class, NumberLiteralEvaluator.class, ComparisonExpressionWithOperatorEvaluator.class})
public class StellarInterpreterTest {
  VariableResolver variableResolver;
  FunctionResolver functionResolver;
  Context context;
  Deque<Token<?>> tokenStack;
  ArithmeticEvaluator arithmeticEvaluator;
  NumberLiteralEvaluator numberLiteralEvaluator;
  ComparisonExpressionWithOperatorEvaluator comparisonExpressionWithOperatorEvaluator;
  StellarCompiler compiler;
  StellarCompiler.Expression expression;

  @SuppressWarnings("unchecked")
  @Before
  public void setUp() throws Exception {
    variableResolver = mock(VariableResolver.class);
    functionResolver = mock(FunctionResolver.class);
    context = mock(Context.class);
    tokenStack = new ArrayDeque<>();
    arithmeticEvaluator = mock(ArithmeticEvaluator.class);
    numberLiteralEvaluator = mock(NumberLiteralEvaluator.class);
    comparisonExpressionWithOperatorEvaluator = mock(ComparisonExpressionWithOperatorEvaluator.class);
    expression = new StellarCompiler.Expression(tokenStack);
    compiler = new StellarCompiler(expression, arithmeticEvaluator, numberLiteralEvaluator, comparisonExpressionWithOperatorEvaluator);
  }

  @Test
  public void exitIntLiteralShouldProperlyParseStringsAsIntegers() throws Exception {
    StellarParser.IntLiteralContext ctx = mock(StellarParser.IntLiteralContext.class);
    Token result = mock(Token.class);
    when(ctx.getText()).thenReturn("1000");
    when(numberLiteralEvaluator.evaluate(ctx)).thenReturn(result);
    compiler.exitIntLiteral(ctx);
    verify(numberLiteralEvaluator).evaluate(ctx);
    Assert.assertEquals(1, tokenStack.size());
    Assert.assertEquals(tokenStack.getFirst(), result);
    verifyZeroInteractions(variableResolver);
    verifyZeroInteractions(functionResolver);
    verifyZeroInteractions(context);
    verifyZeroInteractions(arithmeticEvaluator);
    verifyZeroInteractions(comparisonExpressionWithOperatorEvaluator);
  }

  @Test
  public void exitDoubleLiteralShouldProperlyParseStringsAsDoubles() throws Exception {
    StellarParser.DoubleLiteralContext ctx = mock(StellarParser.DoubleLiteralContext.class);
    Token result = mock(Token.class);
    when(numberLiteralEvaluator.evaluate(ctx)).thenReturn(result);
    when(ctx.getText()).thenReturn("1000D");

    compiler.exitDoubleLiteral(ctx);

    verify(numberLiteralEvaluator).evaluate(ctx);
    Assert.assertEquals(1, tokenStack.size());
    Assert.assertEquals(tokenStack.getFirst(), result);
    verifyZeroInteractions(variableResolver);
    verifyZeroInteractions(functionResolver);
    verifyZeroInteractions(context);
    verifyZeroInteractions(arithmeticEvaluator);
    verifyZeroInteractions(comparisonExpressionWithOperatorEvaluator);
  }

  @Test
  public void exitFloatLiteralShouldProperlyParseStringsAsFloats() throws Exception {
    StellarParser.FloatLiteralContext ctx = mock(StellarParser.FloatLiteralContext.class);
    when(ctx.getText()).thenReturn("1000f");
    Token result = mock(Token.class);
    when(numberLiteralEvaluator.evaluate(ctx)).thenReturn(result);

    compiler.exitFloatLiteral(ctx);

    verify(numberLiteralEvaluator).evaluate(ctx);
    Assert.assertEquals(1, tokenStack.size());
    Assert.assertEquals(tokenStack.getFirst(), result);
    verifyZeroInteractions(variableResolver);
    verifyZeroInteractions(functionResolver);
    verifyZeroInteractions(context);
    verifyZeroInteractions(arithmeticEvaluator);
    verifyZeroInteractions(comparisonExpressionWithOperatorEvaluator);
  }

  @Test
  public void exitLongLiteralShouldProperlyParseStringsAsLongs() throws Exception {
    StellarParser.LongLiteralContext ctx = mock(StellarParser.LongLiteralContext.class);
    when(ctx.getText()).thenReturn("1000l");
    Token result = mock(Token.class);
    when(numberLiteralEvaluator.evaluate(ctx)).thenReturn(result);

    compiler.exitLongLiteral(ctx);

    verify(numberLiteralEvaluator).evaluate(ctx);
    Assert.assertEquals(1, tokenStack.size());
    Assert.assertEquals(tokenStack.getFirst(), result);
    verifyZeroInteractions(variableResolver);
    verifyZeroInteractions(functionResolver);
    verifyZeroInteractions(context);
    verifyZeroInteractions(arithmeticEvaluator);
    verifyZeroInteractions(comparisonExpressionWithOperatorEvaluator);
  }

  @Test
  public void properlyCompareTwoNumbers() throws Exception {
    StellarParser.ComparisonExpressionWithOperatorContext ctx = mock(StellarParser.ComparisonExpressionWithOperatorContext.class);
    StellarParser.ComparisonOpContext mockOp = mock(StellarParser.ComparisonOpContext.class);
    when(ctx.comp_operator()).thenReturn(mockOp);
    Token result = mock(Token.class);
    when(comparisonExpressionWithOperatorEvaluator.evaluate(any(Token.class), any(Token.class), any(StellarParser.ComparisonOpContext.class))).thenReturn(result);

    compiler.exitComparisonExpressionWithOperator(ctx);
    Assert.assertEquals(1, tokenStack.size());
    StellarCompiler.DeferredFunction func = (StellarCompiler.DeferredFunction) tokenStack.pop().getValue();
    tokenStack.push(new Token<>(1000, Integer.class));
    tokenStack.push(new Token<>(1500f, Float.class));
    func.apply(tokenStack, new StellarCompiler.ExpressionState(context, functionResolver, variableResolver));
    Assert.assertEquals(1, tokenStack.size());
    Assert.assertEquals(tokenStack.getFirst(), result);
    verify(comparisonExpressionWithOperatorEvaluator).evaluate(any(Token.class), any(Token.class), eq(mockOp));
    verifyZeroInteractions(numberLiteralEvaluator);
    verifyZeroInteractions(variableResolver);
    verifyZeroInteractions(functionResolver);
    verifyZeroInteractions(context);
    verifyZeroInteractions(arithmeticEvaluator);
  }
}
