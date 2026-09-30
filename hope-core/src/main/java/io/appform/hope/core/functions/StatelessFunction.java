/*
 * Copyright 2019. Santanu Sinha
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing permissions and limitations under the
 * License.
 */

package io.appform.hope.core.functions;

import io.appform.hope.core.visitors.Evaluator;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a {@link HopeFunction} implementation as safe to reuse across evaluations and threads.
 *
 * <p>An implementation carrying this annotation commits to the following contract:</p>
 * <ul>
 *     <li>The instance holds no mutable state that changes across invocations of
 *     {@link HopeFunction#apply(Evaluator.EvaluationContext)}</li>
 *     <li>{@link HopeFunction#apply(Evaluator.EvaluationContext)} produces its result solely from its
 *     constructor-bound parameters, the passed evaluation context, and reads of external state performed
 *     within the invocation (e.g. the system clock, as in date/now or sys/epoch functions)</li>
 *     <li>Concurrent invocations of {@link HopeFunction#apply(Evaluator.EvaluationContext)} on the same
 *     instance are safe</li>
 * </ul>
 * <p>Functions carrying this annotation are constructed once per call site
 * (see {@link io.appform.hope.core.values.FunctionValue#function()}) and the instance is cached and reused
 * for all subsequent evaluations. Functions without this annotation continue to receive a fresh instance
 * on every evaluation, preserving the pre-existing behavior for implementations that use instance fields
 * as per-evaluation scratch space.</p>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface StatelessFunction {
}
