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
import com.atomazing.alba.camunda7.migration.api.CamundaMigrationContext;
import org.apache.commons.lang3.StringUtils;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.repository.ProcessDefinition;
import org.camunda.bpm.engine.repository.ProcessDefinitionQuery;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

import static java.util.Collections.singletonList;

class ProcessMigrator {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessMigrator.class);

    public void applyMigrations(ProcessInstance process, List<CamundaMigration> migrations, ProcessEngine processEngine) {
        LOGGER.info("Migrating process #{} of {}", process.getId(), process.getProcessDefinitionId());
        ProcessDefinition sourceDefinition = getDefinitionById(processEngine, process.getProcessDefinitionId());
        String definitionKey = sourceDefinition.getKey();
        while (true) {
            CamundaMigration migration = findMigrationBySource(migrations, getVersionTag(sourceDefinition));
            if (migration == null) {
                break;
            }
            ProcessDefinition targetDefinition = getDefinitionByKeyAndVersionTag(processEngine, definitionKey, migration.target());
            applyMigration(process, migration, sourceDefinition, targetDefinition, processEngine);

            sourceDefinition = targetDefinition;
        }
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

    private ProcessDefinition getDefinitionByKeyAndVersionTag(ProcessEngine processEngine, String key, String versionTag) {
        ProcessDefinitionQuery query = processEngine.getRepositoryService().createProcessDefinitionQuery();
        if (versionTag == null) {
            query.withoutVersionTag();
        } else {
            query.versionTag(versionTag);
        }
        return query
            .processDefinitionKey(key)
            .orderByProcessDefinitionVersion().desc()
            .list()
            .get(0);
    }

    private CamundaMigration findMigrationBySource(List<CamundaMigration> migrations, String versionTag) {
        return migrations.stream()
            .filter(migration -> Objects.equals(migration.source(), versionTag))
            .findAny()
            .orElse(null);
    }

    private String getDescription(CamundaMigration migration) {
        return migration.key() + " " + migration.source() + " -> " + migration.target();
    }

    private void applyMigration(ProcessInstance process, CamundaMigration migration, ProcessDefinition source, ProcessDefinition target,
        ProcessEngine processEngine) {
        LOGGER.debug("Applying {}", getDescription(migration));
        migration.migrate(new CamundaMigrationContext(processEngine, source, target, singletonList(process.getId())));
    }
}
