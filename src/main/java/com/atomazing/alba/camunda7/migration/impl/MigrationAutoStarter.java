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

import com.atomazing.alba.camunda7.migration.api.CamundaMigration;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.repository.ResourceDefinition;

import java.util.*;
import java.util.function.BiConsumer;

public class MigrationAutoStarter {
    private final Map<String, List<CamundaMigration>> migrationsByKey;

    public MigrationAutoStarter(List<CamundaMigration> migrations) {
        this.migrationsByKey = groupMigrationsByKey(migrations);
    }

    public void autoMigrate(ProcessEngine processEngine) {
        forEachDeployedDefinitionKey(processEngine, this::migrate);
    }

    // ===================================================================================================================
    // = Implementation
    // ===================================================================================================================

    private static Map<String, List<CamundaMigration>> groupMigrationsByKey(List<CamundaMigration> migrations) {
        Map<String, List<CamundaMigration>> byKey = new HashMap<>();
        migrations.forEach(migration -> byKey.computeIfAbsent(migration.key(), any -> new ArrayList<>()).add(migration));
        return byKey;
    }

    private void forEachDeployedDefinitionKey(ProcessEngine processEngine, BiConsumer<String, ProcessEngine> action) {
        processEngine.getRepositoryService().createProcessDefinitionQuery()
            .latestVersion()
            .list().stream()
            .map(ResourceDefinition::getKey)
            .forEach(key -> action.accept(key, processEngine));
    }

    private void migrate(String key, ProcessEngine processEngine) {
        List<CamundaMigration> migrations = migrationsByKey.getOrDefault(key, Collections.emptyList());
        new SyncMigrationStrategy().migrate(key, migrations, processEngine);
    }
}
