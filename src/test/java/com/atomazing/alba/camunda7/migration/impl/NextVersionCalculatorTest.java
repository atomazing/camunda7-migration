/*
 * Copyright 2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.atomazing.alba.camunda7.migration.impl;

import org.camunda.bpm.engine.repository.ProcessDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.aggregator.AggregateWith;
import org.junit.jupiter.params.aggregator.ArgumentsAccessor;
import org.junit.jupiter.params.aggregator.ArgumentsAggregationException;
import org.junit.jupiter.params.aggregator.ArgumentsAggregator;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NextVersionCalculatorTest {
    private NextVersionCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new NextVersionCalculator();
    }

    @ParameterizedTest
    @CsvSource({
        "1.2.3, 1000",
        "1.2.3, 500, 1.2.4, 1000",
        "1.2.3, 1003, 1.2.3, 1001, 1.2.3, 1002",
        "1.2.3, 2000, 1.2.2, 1002",
        "1.2.4, 1501, 1.2.3, 1001, 1.2.3, 1002, 1.2.5, 2000",
        "1.2.4, 1073742324, 1.2.3, 1002, 1.2.5, 2147483647",
    })
    void getNextVersion(String newVersionTag, int nextVersion, @AggregateWith(Aggregator.class) List<ProcessDefinition> definitions) {
        assertEquals(nextVersion, calculator.getNextVersion(mockDefinition(newVersionTag, 0), definitions));
    }

    @SuppressWarnings("unused")
    @ParameterizedTest
    @CsvSource({
        "1.2.3, -1, 1.2.3, 1999, 1.2.4, 2000",
        "1.2.3, -1, 1.2.4, 1"
    })
    void getNextVersion_error(String newVersionTag, int nextVersion, @AggregateWith(Aggregator.class) List<ProcessDefinition> definitions) {
        assertThrows(IllegalArgumentException.class, () -> calculator.getNextVersion(mockDefinition(newVersionTag, 0), definitions));
    }

    // ===================================================================================================================
    // = Implementation
    // ===================================================================================================================

    private static ProcessDefinition mockDefinition(String versionTag, int version) {
        ProcessDefinition mock = Mockito.mock(ProcessDefinition.class);
        Mockito.when(mock.getVersionTag()).thenReturn(versionTag);
        Mockito.when(mock.getVersion()).thenReturn(version);
        return mock;
    }

    private static class Aggregator implements ArgumentsAggregator {
        @Override
        public List<ProcessDefinition> aggregateArguments(ArgumentsAccessor accessor, ParameterContext context) throws ArgumentsAggregationException {
            List<ProcessDefinition> definitions = new ArrayList<>();
            int size = accessor.size();
            for (int i = 2; i < size; i += 2) {
                String versionTag = accessor.getString(i);
                int version = accessor.getInteger(i + 1);
                definitions.add(mockDefinition(versionTag, version));
            }
            return definitions;
        }
    }
}