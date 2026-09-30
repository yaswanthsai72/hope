/*
 * Copyright 2019. Santanu Sinha
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and limitations
 * under the License.
 */

package io.appform.hope.core.values;

import io.appform.hope.core.Value;
import io.appform.hope.core.Visitor;
import io.appform.hope.core.functions.FunctionRegistry;
import io.appform.hope.core.functions.HopeFunction;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.List;

/**
 * An abstraction for a {@link io.appform.hope.core.functions.HopeFunction} call.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class FunctionValue extends Value {
    private final String name;
    private final List<Value> parameters;
    private final FunctionRegistry.ConstructorMeta selectedConstructor;

    /**
     * Lazily constructed and cached {@link HopeFunction} instance for this call site.
     * Marked {@code transient} so that Lombok excludes it from generated {@code equals}/{@code hashCode},
     * and excluded from {@code toString}; accessors are suppressed since the cache must only be
     * managed via {@link #function()}.
     */
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @ToString.Exclude
    private transient volatile HopeFunction<?> cachedFunction;

    /**
     * @param name                Name of the function as provided to {@link io.appform.hope.core.functions.FunctionImplementation}
     * @param parameters          Parameters to be passed to the function
     * @param selectedConstructor Selected overload of the function from {@link FunctionRegistry}
     */
    public FunctionValue(String name, List<Value> parameters, FunctionRegistry.ConstructorMeta selectedConstructor) {
        this.name = name;
        this.parameters = parameters;
        this.selectedConstructor = selectedConstructor;
    }

    /**
     * Returns the {@link HopeFunction} instance for this call site, constructing it on first access and
     * caching it for subsequent evaluations. Since a {@link FunctionValue} is immutable and represents a
     * fixed call site in a parsed expression, the constructed function can safely be reused across
     * evaluations and threads (implementations of {@link HopeFunction#apply} are expected to be stateless
     * with respect to the passed {@link io.appform.hope.core.visitors.Evaluator.EvaluationContext}).
     *
     * @return the (cached) function instance for this call site
     * @throws IllegalArgumentException if the function instance cannot be constructed
     */
    public HopeFunction<?> function() {
        final HopeFunction<?> result = cachedFunction;
        if (result != null) {
            return result;
        }
        synchronized (this) {
            HopeFunction<?> current = cachedFunction;
            if (current == null) {
                current = createFunction();
                cachedFunction = current;
            }
            return current;
        }
    }

    private HopeFunction<?> createFunction() {
        try {
            final var constructor = selectedConstructor.getConstructor();
            if (selectedConstructor.isHasVariableArgs()) {
                return constructor.newInstance((Object) parameters.toArray(new Value[0]));
            }
            else {
                return constructor.newInstance(parameters.toArray(new Object[0]));
            }
        }
        catch (Exception e) {
            throw new IllegalArgumentException("Could not create instance of function: '" + name + "'", e);
        }
    }

    @Override
    public <T> T accept(Visitor<T> visitor) {
        return visitor.visit(this);
    }
}
