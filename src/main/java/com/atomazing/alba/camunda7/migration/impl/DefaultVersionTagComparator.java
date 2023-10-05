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

import java.util.Comparator;
import java.util.Objects;

class DefaultVersionTagComparator implements Comparator<String> {
    @Override
    public int compare(String tag1, String tag2) {
        tag1 = StringUtils.trimToNull(tag1);
        tag2 = StringUtils.trimToNull(tag2);
        if (Objects.equals(tag1, tag2)) {
            return 0;
        } else if (tag1 == null) {
            return -1;
        } else if (tag2 == null) {
            return 1;
        }
        return new ComparableVersion(tag1).compareTo(new ComparableVersion(tag2));
    }
}
