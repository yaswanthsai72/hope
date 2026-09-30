package io.appform.hope.core.values;

import io.appform.hope.core.functions.FunctionRegistry;
import io.appform.hope.core.functions.HopeFunction;
import io.appform.hope.core.functions.impl.math.Abs;
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
    void function_IsCachedPerCallSite() {
        final var functionValue = sampleFunctionValue();

        final var first = functionValue.function();
        final var second = functionValue.function();
        assertNotNull(first);
        assertSame(first, second, "repeated calls must return the same cached instance");
    }

    @Test
    void function_SingleInstanceAcrossConcurrentCalls() throws Exception {
        final var functionValue = sampleFunctionValue();

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
    void equalsAndHashCode_IgnoreCachedFunction() {
        final var left = sampleFunctionValue();
        final var right = sampleFunctionValue();

        left.function();

        assertEquals(left, right, "cached function must not affect equality");
        assertEquals(left.hashCode(), right.hashCode(), "cached function must not affect hashCode");
        assertFalse(left.toString().contains("cachedFunction"),
                    "cached function must not appear in toString");
    }

    private FunctionValue sampleFunctionValue() {
        final var registry = new FunctionRegistry();
        registry.register(Abs.class);
        final FunctionRegistry.ConstructorMeta constructorMeta =
                registry.find("math.abs")
                        .flatMap(meta -> meta.getConstructors().stream().findFirst())
                        .orElseThrow(() -> new AssertionError("math.abs must be registered"));
        return new FunctionValue("math.abs", List.of(new NumericValue(1)), constructorMeta);
    }
}
