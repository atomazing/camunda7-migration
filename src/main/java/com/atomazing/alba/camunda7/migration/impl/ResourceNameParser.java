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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Получает имя файла из пути к файлу (пример пути файла - /app/teamcity-ci-cdl-agent/di-tcdd-ag231lv-01/work/853fab4872c84c64/target/classes/bpmn/000000/loan-app-completed-notification.bpmn)
 */
class ResourceNameParser {
    private static final Pattern NAME = Pattern.compile("^(.+?)(?:[a-zA-Z-]+-(\\d[\\d.]*?(-(SNAPSHOT|RELEASE))?))?\\.(bpmn?|bpmn20\\.xml?|zip)$");

    public String parseVersion(String resourceName) {
        Matcher matcher = NAME.matcher(resourceName);
        if (matcher.matches()) {
            return matcher.group(2);
        }
        throw new IllegalArgumentException("Bad resource name: " + resourceName);
    }
}
