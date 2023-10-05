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

import lombok.RequiredArgsConstructor;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.impl.interceptor.Command;
import org.camunda.bpm.engine.impl.interceptor.CommandContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RequiredArgsConstructor
class AutoMigrateCmd implements Command<Void> {
    private static final Logger LOGGER = LoggerFactory.getLogger(AutoMigrateCmd.class);

    private final MigrationAutoStarter migrationAutoStarter;
    private final ProcessEngine processEngine;

    @Override
    public Void execute(CommandContext commandContext) {
        acquireExclusiveLock(commandContext);
        migrationAutoStarter.autoMigrate(processEngine);
        return null;
    }

    private void acquireExclusiveLock(CommandContext commandContext) {
        if (commandContext.getProcessEngineConfiguration().isDeploymentLockUsed()) {
            commandContext.getPropertyManager().acquireExclusiveLock();
            LOGGER.debug("Acquired exclusive db lock");
        }
    }
}
