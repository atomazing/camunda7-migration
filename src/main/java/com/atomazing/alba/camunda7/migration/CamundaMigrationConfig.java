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
package com.atomazing.alba.camunda7.migration;

import com.atomazing.alba.camunda7.migration.api.CamundaMigration;
import com.atomazing.alba.camunda7.migration.impl.MigratingSpringProcessEngineConfiguration;
import com.atomazing.alba.camunda7.migration.impl.MigrationAutoStarter;
import org.camunda.bpm.engine.impl.cfg.CompositeProcessEnginePlugin;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.cfg.ProcessEnginePlugin;
import org.camunda.bpm.engine.spring.SpringProcessEngineConfiguration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

import static org.camunda.bpm.spring.boot.starter.util.CamundaSpringBootUtil.initCustomFields;

@Configuration
public class CamundaMigrationConfig {
    @Value("${camunda.bpm.application.deploy-changed-only:true}")
    private boolean deployChangedOnly;

    @Bean
    public ProcessEngineConfigurationImpl processEngineConfigurationImpl(
        List<ProcessEnginePlugin> processEnginePlugins,
        List<CamundaMigration> camundaMigrations
    ) {
        MigrationAutoStarter migrationAutoStarter = new MigrationAutoStarter(camundaMigrations);
        SpringProcessEngineConfiguration configuration =
            initCustomFields(new MigratingSpringProcessEngineConfiguration(migrationAutoStarter, deployChangedOnly));
        configuration.getProcessEnginePlugins().add(new CompositeProcessEnginePlugin(processEnginePlugins));
        return configuration;
    }
}
