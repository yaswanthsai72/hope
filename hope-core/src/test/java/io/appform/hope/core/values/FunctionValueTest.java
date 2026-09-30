package io.appform.hope.core.values;

import io.appform.hope.core.Value;
import io.appform.hope.core.functions.FunctionImplementation;
import io.appform.hope.core.functions.FunctionRegistry;
import io.appform.hope.core.functions.HopeFunction;
import io.appform.hope.core.functions.StatelessFunction;
import io.appform.hope.core.functions.impl.math.Abs;
import io.appform.hope.core.visitors.Evaluator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class FunctionValueTest {

    @Test
    void function_IsCachedForStatelessImplementations() {
        final var functionValue = functionValue(Abs.class, "math.abs");

        final var first = functionValue.function();
        final var second = functionValue.function();
        assertNotNull(first);
        assertSame(first, second, "stateless implementations must be cached per call site");
    }

    @Test
    void function_SingleInstanceAcrossConcurrentCallsForStatelessImplementations() throws Exception {
        final var functionValue = functionValue(Abs.class, "math.abs");

        final ExecutorService executor = Executors.newFixedThreadPool(16);
        try {
            final var futures = IntStream.range(0, 256)
                    .mapToObj(i -> CompletableFuture.supplyAsync(functionValue::function, executor))
                    .toArray(CompletableFuture[]::new);
            CompletableFuture.allOf(futures).get(30, TimeUnit.SECONDS);

            final var firstResult = ((CompletableFuture<HopeFunction<?>>) futures[0]).get();
            for (final var future : futures) {
                assertSame(firstResult, future.get(),
                           "all concurrent callers must observe the same cached instance");
            }
        }
        finally {
            executor.shutdownNow();
        }
    }

    @Test
    void function_FreshInstancePerEvaluationForUnannotatedImplementations() {
        final var functionValue = functionValue(StatefulScratchFunction.class, "test.scratch");

        final var first = functionValue.function();
        final var second = functionValue.function();
        assertNotNull(first);
        assertNotSame(first, second,
                      "unannotated implementations must receive a fresh instance per evaluation");
    }

    @Test
    void function_StatefulScratchBehaviorPreservedAcrossEvaluations() {
        final var functionValue = functionValue(StatefulScratchFunction.class, "test.scratch");

        // Each apply() toggles the instance scratch field; a fresh instance per evaluation must
        // therefore always observe the initial scratch state and return the same result.
        for (int i = 0; i < 10; i++) {
            final var result = (StatefulScratchFunction) functionValue.function();
            assertEquals(new BooleanValue(false), result.apply(null),
                         "evaluation " + i + " must see a fresh instance with initial scratch state");
        }
    }

    @Test
    void equalsAndHashCode_IgnoreCachedFunction() {
        final var left = functionValue(Abs.class, "math.abs");
        final var right = functionValue(Abs.class, "math.abs");

        left.function();

        assertEquals(left, right, "cached function must not affect equality");
        assertEquals(left.hashCode(), right.hashCode(), "cached function must not affect hashCode");
        assertFalse(left.toString().contains("cachedFunction"),
                    "cached function must not appear in toString");
    }

    @Test
    void parameters_SnapshottedForCacheableFunctions() {
        final var parameters = new java.util.ArrayList<Value>(List.of(new NumericValue(1)));
        final var functionValue = functionValue(Abs.class, "math.abs", parameters);

        parameters.set(0, new NumericValue(999));

        assertEquals(new NumericValue(1), functionValue.getParameters().get(0),
                     "mutating the supplied list must not affect the bound parameters of a cached call site");
        assertThrows(UnsupportedOperationException.class, () -> functionValue.getParameters().set(0, new NumericValue(2)),
                     "parameters of a cacheable call site must be unmodifiable");
    }

    @Test
    void parameters_RetainedAsIsForUnannotatedFunctions() {
        final var parameters = new java.util.ArrayList<Value>(List.of(new NumericValue(1)));
        final var functionValue = functionValue(StatefulScratchFunction.class, "test.scratch", parameters);

        parameters.set(0, new NumericValue(999));

        assertEquals(new NumericValue(999), functionValue.getParameters().get(0),
                     "supplied list must be retained as-is for unannotated functions");
    }

    private FunctionValue functionValue(Class<? extends HopeFunction> clazz, String name) {
        return functionValue(clazz, name, List.of(new NumericValue(1)));
    }

    private FunctionValue functionValue(Class<? extends HopeFunction> clazz, String name,
                                         List<Value> parameters) {
        final var registry = new FunctionRegistry();
        registry.register(clazz);
        final FunctionRegistry.ConstructorMeta constructorMeta =
                registry.find(name)
                        .flatMap(meta -> meta.getConstructors().stream().findFirst())
                        .orElseThrow(() -> new AssertionError(name + " must be registered"));
        return new FunctionValue(name, parameters, constructorMeta);
    }

    /**
     * A deliberately stateful function using an instance field as per-evaluation scratch space;
     * intentionally NOT annotated with {@link StatelessFunction}.
     */
    @FunctionImplementation("test.scratch")
    static class StatefulScratchFunction extends HopeFunction<BooleanValue> {

        private final Value param;
        private int scratch;

        public StatefulScratchFunction(Value param) {
            this.param = param;
        }

        @Override
        public BooleanValue apply(Evaluator.EvaluationContext evaluationContext) {
            scratch++;
            return new BooleanValue(scratch % 2 == 0);
        }
    }
}
