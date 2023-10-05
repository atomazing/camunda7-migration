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

import org.apache.commons.lang3.StringUtils;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.impl.bpmn.deployer.BpmnDeployer;
import org.camunda.bpm.engine.impl.core.model.Properties;
import org.camunda.bpm.engine.impl.persistence.entity.DeploymentEntity;
import org.camunda.bpm.engine.impl.persistence.entity.ProcessDefinitionEntity;
import org.camunda.bpm.engine.impl.persistence.entity.ResourceEntity;
import org.camunda.bpm.engine.repository.ProcessDefinition;
import org.camunda.bpm.engine.repository.ResourceDefinition;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;

class VersionTagAwareBpmnDeployer extends BpmnDeployer {
    private static final Logger LOGGER = LoggerFactory.getLogger(VersionTagAwareBpmnDeployer.class);
    private static final String FOUND_OVERRIDEN_VERSION_TAG_PROCESSES = "Deployed definition {}#{}, found {} active processes on older definitions with the " +
        "same version tag. These processes will not be migrated to mentioned definition, instead auto-migration will attempt to migrate them directly to " +
        "newer version tag if it finds one.";

    private final NextVersionCalculator nextVersionCalculator;
    private final ResourceNameParser resourceNameParser;

    public VersionTagAwareBpmnDeployer() {
        this.nextVersionCalculator = new NextVersionCalculator();
        this.resourceNameParser = new ResourceNameParser();
    }

    @Override
    protected List<ProcessDefinitionEntity> transformDefinitions(DeploymentEntity deployment, ResourceEntity resource, Properties properties) {
        List<ProcessDefinitionEntity> definitions = super.transformDefinitions(deployment, resource, properties);
        definitions.forEach(definition -> validate(definition, resource, deployment));
        return definitions;
    }

    @Override
    protected int getNextVersion(DeploymentEntity deployment, ProcessDefinitionEntity definition, ProcessDefinitionEntity ignore) {
        List<ProcessDefinition> definitions = getProcessDefinitionsByKey(definition.getKey());
        return nextVersionCalculator.getNextVersion(definition, definitions);
    }

    @Override
    protected void persistDefinition(ProcessDefinitionEntity definition) {
        super.persistDefinition(definition);
        checkOverridenVersionTag(definition);
    }

    // ===================================================================================================================
    // = Implementation
    // ===================================================================================================================

    private RuntimeService getRuntimeService() {
        return getProcessEngineConfiguration().getRuntimeService();
    }

    private List<ProcessDefinition> getProcessDefinitionsByKey(String key) {
        List<ProcessDefinition> definitions = new ArrayList<>(getProcessDefinitionManager().findProcessDefinitionsByKey(key));
        // findProcessDefinitionsByKey не учитывает кэш :(
        Set<ProcessDefinition> cachedDefinitions = getDbEntityManager().getCachedEntitiesByType(ProcessDefinitionEntity.class)
            .stream()
            .filter(definition -> key.equals(definition.getKey()))
            .collect(Collectors.toSet());
        definitions.addAll(cachedDefinitions);
        return definitions;
    }

    private void validate(ProcessDefinitionEntity definition, ResourceEntity resource, DeploymentEntity deployment) {
        String definitionVersion = getVersionTag(definition);
        String resourceVersion = getVersionTag(resource);
        Assert.isTrue(Objects.equals(definitionVersion, resourceVersion),
            () -> String.format("Deployment %s version mismatch: definition %s #%s doesn't match resource %s #%s",
                deployment.getName(), definition.getKey(), definitionVersion, resource.getName(), resourceVersion));
    }

    private String getVersionTag(ProcessDefinition definition) {
        return StringUtils.trimToNull(definition.getVersionTag());
    }

    private String getVersionTag(ResourceEntity resource) {
        return resourceNameParser.parseVersion(resource.getName());
    }

    private List<String> findOverridenVersionTagDefinitionIds(ProcessDefinitionEntity definition) {
        return getProcessDefinitionsByKey(definition.getKey()).stream()
            .filter(other -> Objects.equals(getVersionTag(other), getVersionTag(definition)))
            .map(ResourceDefinition::getId)
            .filter(id -> !Objects.equals(id, definition.getId()))
            .collect(toList());
    }

    private void checkOverridenVersionTag(ProcessDefinitionEntity definition) {
        List<String> overridenDefinitionIds = findOverridenVersionTagDefinitionIds(definition);
        List<ProcessInstance> processes = getRuntimeService().createProcessInstanceQuery()
            .processDefinitionKey(definition.getKey())
            .list().stream()
            .filter(process -> overridenDefinitionIds.contains(process.getProcessDefinitionId()))
            .collect(toList());
        if (!processes.isEmpty()) {
            LOGGER.warn(FOUND_OVERRIDEN_VERSION_TAG_PROCESSES, definition.getId(), definition.getVersionTag(), processes.size());
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("{}", processes.stream().map(process -> process.getId() + "@" + process.getProcessDefinitionId()).collect(toList()));
            }
        }
    }
}
