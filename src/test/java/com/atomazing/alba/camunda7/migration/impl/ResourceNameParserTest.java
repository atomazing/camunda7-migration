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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ResourceNameParserTest {
    @ParameterizedTest
    @CsvSource(value = {
        "some-process.bpmn___ ",
        "some-process.bpmn20.xml___ ",
        "some-process.zip___ ",
        "some-process-1.2.3.bpmn___1.2.3",
        "some-process-1.2.3.bpmn20.xml___1.2.3",
        "some-process-1.2.3.zip___1.2.3",
        "some-process-5.bpmn___5",
        "some-process-1.2.3.4-SNAPSHOT.bpmn___1.2.3.4-SNAPSHOT",
    }, delimiterString = "___")
    void parseVersion(String name, String version) {
        assertEquals(version, new ResourceNameParser().parseVersion(name));
    }
}