/*
 * (c) Copyright 2025 Palantir Technologies Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.palantir.javaformat.gradle;

import javax.inject.Inject;
import org.gradle.api.provider.ProviderFactory;

public abstract class NativeImageSupport {

    @Inject
    protected abstract ProviderFactory getProviderFactory();

    public boolean isNativeImageConfigured() {
        return isNativeFlagEnabled() && isNativeImageSupported();
    }

    /** Whether this platform has a published native image; see NativePlatform's PUBLISHED. */
    private boolean isNativeImageSupported() {
        return NativePlatform.current(getProviderFactory())
                .map(NativePlatform::isPublished)
                .get();
    }

    private boolean isNativeFlagEnabled() {
        return getProviderFactory()
                .gradleProperty("openjavaformat.native.formatter")
                .map(Boolean::parseBoolean)
                .orElse(false)
                .get();
    }
}
