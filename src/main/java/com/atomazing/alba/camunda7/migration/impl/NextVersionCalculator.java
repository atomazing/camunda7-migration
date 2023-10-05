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

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.camunda.bpm.engine.repository.ProcessDefinition;
import org.camunda.bpm.engine.repository.ResourceDefinition;
import org.springframework.util.Assert;

import java.util.*;
import java.util.function.Function;

class NextVersionCalculator {
    private static final int VERSION_RESERVE = 1000;

    public int getNextVersion(ProcessDefinition newDefinition, List<ProcessDefinition> existingDefinitions) {
        Pair<ProcessDefinition, ProcessDefinition> neighbours = getNeighbours(newDefinition, existingDefinitions);
        return getVersionBetween(newDefinition, neighbours.getLeft(), neighbours.getRight());
    }

    // ===================================================================================================================
    // = Implementation
    // ===================================================================================================================

    private Pair<ProcessDefinition, ProcessDefinition> getNeighbours(ProcessDefinition definition, List<ProcessDefinition> definitions) {
        if (definitions.isEmpty()) {
            return new ImmutablePair<>(null, null);
        }
        List<ProcessDefinition> sorted = new ArrayList<>(definitions);
        sorted.add(definition);
        sorted.sort(createComparator());
        int index = sorted.indexOf(definition);
        ProcessDefinition left = index > 0 ? sorted.get(index - 1) : null;
        ProcessDefinition right = index < sorted.size() - 1 ? sorted.get(index + 1) : null;
        return new ImmutablePair<>(left, right);
    }

    private Comparator<ProcessDefinition> createComparator() {
        // "" 1 < "" 2 < "alpha" 3 < "alpha" 4 < "alpha" 0 < "beta" 5
        return Comparator.comparing(ProcessDefinition::getVersionTag, new DefaultVersionTagComparator())
            .thenComparing(ResourceDefinition::getVersion, this::compareVersions);
    }

    private int compareVersions(int v1, int v2) {
        if (v1 == v2) {
            return 0;
        } else if (v1 == 0) {
            return 1;
        } else if (v2 == 0) {
            return -1;
        } else {
            return v1 - v2;
        }
    }

    private int getVersionBetween(ProcessDefinition definition, ProcessDefinition left, ProcessDefinition right) {
        int version;
        if (left == null) {
            // null < 1000 < null
            // null < 500 < 1000
            version = right == null ? VERSION_RESERVE : right.getVersion() / 2;
        } else if (Objects.equals(left.getVersionTag(), definition.getVersionTag())) {
            // 1002 < 1003 < *
            version = left.getVersion() + 1;
        } else if (right == null) {
            // 1002 < 2000 < null
            version = (left.getVersion() / VERSION_RESERVE + 1) * VERSION_RESERVE;
        } else {
            // 1002 < 1501 < 2000
            version = left.getVersion() / 2 + right.getVersion() / 2;
        }
        Assert.isTrue(version > 0 && (left == null || left.getVersion() < version) && (right == null || version < right.getVersion()),
            () -> String.format("Failed to get version between %s (%s) < %s < %s (%s)",
                map(left, ResourceDefinition::getVersion), map(left, ProcessDefinition::getVersionTag),
                version,
                map(right, ResourceDefinition::getVersion), map(right, ProcessDefinition::getVersionTag)));
        return version;
    }

    private <O, V> V map(O object, Function<O, V> getter) {
        return Optional.ofNullable(object).map(getter).orElse(null);
    }
}
