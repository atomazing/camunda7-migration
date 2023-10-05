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

import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.ProcessEngineException;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.impl.interceptor.Command;
import org.camunda.bpm.engine.impl.interceptor.CommandContext;
import org.camunda.bpm.engine.repository.DeploymentBuilder;
import org.camunda.bpm.engine.repository.DeploymentWithDefinitions;
import org.camunda.bpm.engine.repository.ProcessDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ContextResource;
import org.springframework.core.io.Resource;
import org.springframework.util.Assert;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipInputStream;

class VersionTagAwareDeployCmd implements Command<Void> {
    private static final Logger LOGGER = LoggerFactory.getLogger(VersionTagAwareDeployCmd.class);

    private final String deploymentName;
    private final Resource[] deploymentResources;
    private final String deploymentTenantId;
    private final boolean deployChangedOnly;
    private final ProcessEngine processEngine;

    private final ResourceNameParser resourceNameParser;

    public VersionTagAwareDeployCmd(String deploymentName, Resource[] deploymentResources, String deploymentTenantId, boolean deployChangedOnly,
        ProcessEngine processEngine) {
        this.deploymentName = deploymentName;
        this.deploymentResources = deploymentResources;
        this.deploymentTenantId = deploymentTenantId;
        this.deployChangedOnly = deployChangedOnly;
        this.processEngine = processEngine;

        this.resourceNameParser = new ResourceNameParser();
    }

    @Override
    public Void execute(CommandContext commandContext) {
        LOGGER.info("Found {} resources", deploymentResources.length);
        acquireExclusiveLock(commandContext);
        deploy();
        return null;
    }

    private void acquireExclusiveLock(CommandContext commandContext) {
        if (commandContext.getProcessEngineConfiguration().isDeploymentLockUsed()) {
            commandContext.getPropertyManager().acquireExclusiveLock();
            LOGGER.debug("Acquired exclusive db lock");
        }
    }

    private List<ProcessDefinition> deploy() {
        List<ProcessDefinition> definitions = groupResources().stream()
            .map(this::deployGroup)
            .flatMap(this::deployedProcessDefinitionsStream)
            .collect(Collectors.toList());
        LOGGER.info("Deployed {} definitions", definitions.size());
        LOGGER.debug("{}", definitions.stream().map(this::getDescription).collect(Collectors.toList()));
        return definitions;
    }

    private Stream<ProcessDefinition> deployedProcessDefinitionsStream(DeploymentWithDefinitions deployment) {
        return Optional.ofNullable(deployment.getDeployedProcessDefinitions())
            .orElse(Collections.emptyList())
            .stream();
    }

    private String getDescription(ProcessDefinition definition) {
        return definition.getId() + "#" + definition.getVersionTag();
    }

    private List<List<Resource>> groupResources() {
        Map<String, List<Resource>> groups = new TreeMap<>(new DefaultVersionTagComparator());
        for (Resource resource : deploymentResources) {
            String version = resourceNameParser.parseVersion(getResourceName(resource));
            groups.computeIfAbsent(version, any -> new ArrayList<>()).add(resource);
        }
        LOGGER.debug("Grouped resources into versions: {}", groups.keySet());
        return new ArrayList<>(groups.values());
    }

    private String getFileResourceName(Resource resource) {
        return resource.getFilename();
    }

    private String getResourceName(Resource resource) {
        if (resource instanceof ContextResource) {
            return ((ContextResource) resource).getPathWithinContext();
        } else if (resource instanceof ByteArrayResource) {
            return resource.getDescription();
        } else {
            return getFileResourceName(resource);
        }
    }

    private void addResourceToDeployment(DeploymentBuilder deploymentBuilder, Resource resource) {
        String resourceName = getResourceName(resource);
        Assert.notNull(resourceName, "No name for " + resource);
        try {
            if (resourceName.endsWith(".bar")
                || resourceName.endsWith(".zip")
                || resourceName.endsWith(".jar")) {
                deploymentBuilder.addZipInputStream(new ZipInputStream(resource.getInputStream()));
            } else {
                deploymentBuilder.addInputStream(resourceName, resource.getInputStream());
            }
        } catch (IOException e) {
            throw new ProcessEngineException("couldn't auto deploy resource '" + resource + "': " + e.getMessage(), e);
        }
    }

    private DeploymentWithDefinitions deployGroup(List<Resource> resourceGroup) {
        RepositoryService repositoryService = processEngine.getRepositoryService();
        DeploymentBuilder deploymentBuilder = repositoryService
            .createDeployment()
            .enableDuplicateFiltering(deployChangedOnly)
            .name(deploymentName)
            .tenantId(deploymentTenantId);
        resourceGroup.forEach(resource -> addResourceToDeployment(deploymentBuilder, resource));
        return deploymentBuilder.deployWithResult();
    }
}
