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
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.repository.ProcessDefinition;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Setter
class SyncMigrationStrategy {
    private static final Logger LOGGER = LoggerFactory.getLogger(SyncMigrationStrategy.class);

    public void migrate(String key, List<CamundaMigration> migrations, ProcessEngine processEngine) {
        List<ProcessInstance> processes = getMigratingProcesses(key, migrations, processEngine);
        LOGGER.info("For {} migrating {} processes", key, processes.size());
        if (processes.isEmpty()) {
            return;
        }
        migrateProcesses(processes, migrations, processEngine);
    }

    // ===================================================================================================================
    // = Implementation
    // ===================================================================================================================

    private String getVersionTag(ProcessDefinition definition) {
        return StringUtils.trimToNull(definition.getVersionTag());
    }

    private ProcessDefinition getDefinitionById(ProcessEngine processEngine, String definitionId) {
        return processEngine.getRepositoryService().getProcessDefinition(definitionId);
    }

    private boolean isMigrationSource(ProcessInstance process, List<CamundaMigration> migrations, ProcessEngine processEngine) {
        ProcessDefinition definition = getDefinitionById(processEngine, process.getProcessDefinitionId());
        String versionTag = getVersionTag(definition);
        return migrations.stream()
            .map(CamundaMigration::source)
            .anyMatch(source -> Objects.equals(source, versionTag));
    }

    private List<ProcessInstance> getMigratingProcesses(String key, List<CamundaMigration> migrations, ProcessEngine processEngine) {
        if (migrations.isEmpty()) {
            return Collections.emptyList();
        }
        return processEngine.getRuntimeService().createProcessInstanceQuery()
            .processDefinitionKey(key)
            .list().stream()
            .filter(process -> isMigrationSource(process, migrations, processEngine))
            .collect(Collectors.toList());
    }

    private void migrateProcesses(List<ProcessInstance> processes, List<CamundaMigration> migrations, ProcessEngine processEngine) {
        processes.forEach(process -> new ProcessMigrator().applyMigrations(process, migrations, processEngine));
    }
}
