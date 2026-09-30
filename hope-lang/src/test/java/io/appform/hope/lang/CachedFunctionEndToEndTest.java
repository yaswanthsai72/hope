package io.appform.hope.lang;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.StringReader;
import java.util.Collections;
import java.util.stream.IntStream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import io.appform.hope.core.Evaluatable;
import io.appform.hope.core.exceptions.errorstrategy.InjectValueErrorHandlingStrategy;
import io.appform.hope.core.functions.FunctionRegistry;
import io.appform.hope.core.visitors.Evaluator;
import io.appform.hope.lang.parser.HopeParser;
import lombok.SneakyThrows;
import lombok.val;
import org.junit.jupiter.params.provider.Arguments;
import java.util.stream.Stream;

/**
 * End-to-end verification that a single parsed rule — whose function call sites therefore hold
 * cached {@link io.appform.hope.core.functions.HopeFunction} instances for annotated built-ins —
 * evaluates correctly against many different payloads. This guards against the cached instance
 * retaining any state or payload binding from a previous evaluation.
 */
class CachedFunctionEndToEndTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final FunctionRegistry functionRegistry;
    private final Evaluator evaluator;

    CachedFunctionEndToEndTest() {
        this.functionRegistry = new FunctionRegistry();
        functionRegistry.discover(Collections.emptyList());
        this.evaluator = new Evaluator(new InjectValueErrorHandlingStrategy());
    }

    @ParameterizedTest
    @MethodSource("rulePayloadPairs")
    @SneakyThrows
    void sameParsedRuleEvaluatesCorrectlyAcrossPayloads(String rule, String[] jsonPayloads,
                                                         boolean[] expected) {
        val parsed = parse(rule);

        for (int i = 0; i < jsonPayloads.length; i++) {
            val node = MAPPER.readTree(jsonPayloads[i]);
            assertEquals(expected[i],
                         evaluator.evaluate(parsed, node),
                         "evaluation " + i + " of rule [" + rule + "] on ["
                                 + jsonPayloads[i] + "] must be independent of prior evaluations");
        }

        // Re-evaluate the earlier payloads once more: results must be identical even though the
        // function instances have now been reused across many different payloads.
        for (int i = 0; i < jsonPayloads.length; i++) {
            val node = MAPPER.readTree(jsonPayloads[i]);
            assertEquals(expected[i],
                         evaluator.evaluate(parsed, node),
                         "re-evaluation " + i + " of rule [" + rule + "] on ["
                                 + jsonPayloads[i] + "] must match the first run");
        }
    }

    static Stream<Arguments> rulePayloadPairs() {
        return Stream.of(
                // math.abs on a jsonpath value
                Arguments.of("math.abs(\"$.amount\") > 100",
                             new String[]{"{\"amount\": -250}", "{\"amount\": -50}", "{\"amount\": 750}"},
                             new boolean[]{true, false, true}),
                // str.upper on payload data
                Arguments.of("str.upper(\"$.name\") == \"ALICE\"",
                             new String[]{"{\"name\": \"alice\"}", "{\"name\": \"bob\"}", "{\"name\": \"Alice\"}"},
                             new boolean[]{true, false, true}),
                // arr.in against payload arrays
                Arguments.of("arr.in(\"$.needle\", \"$.haystack\") == true",
                             new String[]{"{\"needle\": 2, \"haystack\": [1, 2, 3]}",
                                     "{\"needle\": 9, \"haystack\": [1, 2, 3]}",
                                     "{\"needle\": \"b\", \"haystack\": [\"a\", \"b\"]}"},
                             new boolean[]{true, false, true}),
                // nested functions: math.abs inside arr.in
                Arguments.of("arr.in(math.abs(\"$.delta\"), \"$.values\") == true",
                             new String[]{"{\"delta\": -2, \"values\": [1, 2, 3]}",
                                     "{\"delta\": -7, \"values\": [1, 2, 3]}",
                                     "{\"delta\": 3, \"values\": [1, 2, 3]}"},
                             new boolean[]{true, false, true}));
    }

    @Test
    @SneakyThrows
    void repeatedEvaluationOfSameRuleIsStableUnderInterleavedPayloads() {
        val rule = parse("math.abs(\"$.amount\") >= 100");
        val bigPayload = MAPPER.readTree("{\"amount\": -1000}");
        val smallPayload = MAPPER.readTree("{\"amount\": -1}");

        // Interleave evaluations against different payloads on the SAME parsed rule to surface any
        // cross-evaluation contamination of cached function instances.
        IntStream.range(0, 100).forEach(i -> {
            assertEquals(true, evaluator.evaluate(rule, bigPayload));
            assertEquals(false, evaluator.evaluate(rule, smallPayload));
        });
    }

    @SneakyThrows
    private Evaluatable parse(String rule) {
        return new HopeParser(new StringReader(rule)).parse(functionRegistry);
    }
}
