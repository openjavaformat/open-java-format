/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
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

import groovy.util.Node;
import java.lang.module.ModuleDescriptor;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class ConfigureJavaFormatterXml {

    static void configureJavaFormat(Node rootNode, List<URI> uris) {
        var settings = matchOrCreateChild(rootNode, "component", Map.of("name", "PalantirJavaFormatSettings"));
        // enable
        attributes(matchOrCreateChild(settings, "option", Map.of("name", "enabled")))
                .put("value", "true");
        // configure classpath
        var classPath = matchOrCreateChild(settings, "option", Map.of("name", "implementationClassPath"));
        var listItems = matchOrCreateChild(classPath, "list", Map.of());
        listItems.children().clear();
        for (var uri : uris) {
            listItems.appendNode("option", new LinkedHashMap<>(Map.of("value", uri.toString())));
        }
        // Earlier versions wrote the native binary for openjavaformat.native.formatter=true. The IDE plugin has a
        // setting of its own for that now, useNativeImage, which is left alone, and no longer reads this option.
        matchChild(settings, "option", Map.of("name", "nativeImageClassPath"))
                .ifPresent(option -> option.parent().remove(option));
    }

    static void configureWorkspaceXml(Node rootNode) {
        configureFormatOnSave(rootNode);
        configureOptimizeOnSave(rootNode);
    }

    // Lists an IDEA plugin as required by the project, so IntelliJ offers to install or update it. The other
    // required plugins stay, and so does a higher min-version for this one, such as a developer's own choice.
    static void configureExternalDependencies(Node rootNode, String pluginId, String minVersion) {
        var externalDependencies = matchOrCreateChild(rootNode, "component", Map.of("name", "ExternalDependencies"));
        var plugin = matchOrCreateChild(externalDependencies, "plugin", Map.of("id", pluginId));
        var existing = plugin.attribute("min-version");
        var required = ModuleDescriptor.Version.parse(minVersion);
        if (existing == null
                || ModuleDescriptor.Version.parse(existing.toString()).compareTo(required) < 0) {
            attributes(plugin).put("min-version", minVersion);
        }
    }

    private static void configureFormatOnSave(Node rootNode) {
        configureOnSaveAction(matchOrCreateChild(rootNode, "component", Map.of("name", "FormatOnSaveOptions")));
    }

    private static void configureOptimizeOnSave(Node rootNode) {
        configureOnSaveAction(matchOrCreateChild(rootNode, "component", Map.of("name", "OptimizeOnSaveOptions")));
    }

    private static void configureOnSaveAction(Node onSaveOptions) {
        // In IntelliJ <2023.3.7, the myRunOnSave option is default false.
        // In IntelliJ >=2023.3.7, it is default true, and if it is set to true IntelliJ will remove it from the XML
        //     the next time the file is saved or the actions on save options window is saved.
        // So to cover all cases, we always write it out and ensure it is true
        var myRunOnSave = matchOrCreateChild(onSaveOptions, "option", Map.of("name", "myRunOnSave"));
        attributes(myRunOnSave).put("value", "true");

        // In IntelliJ <2023.3.7, the myAllFileTypesSelected option is default true.
        // In IntelliJ >=2023.3.7, it is default false, and if it is set to false IntelliJ will remove it from the XML
        //     the next time the file is saved or the actions on save options window is saved.
        // People may have manually set this to true, so we respect that setting is so. However, if it doesn't exist,
        //     we make sure it is explicitly set false to work in all IntelliJ versions.
        matchOrCreateChild(onSaveOptions, "option", Map.of("name", "myAllFileTypesSelected"), Map.of("value", "false"));

        // Ensure that is per-file type settings are enabled, we ensure JAVA is part of the list.
        var mySelectedFileTypes = matchOrCreateChild(onSaveOptions, "option", Map.of("name", "mySelectedFileTypes"));
        var set = matchOrCreateChild(mySelectedFileTypes, "set", Map.of());
        matchOrCreateChild(set, "option", Map.of("value", "JAVA"));
    }

    private static Node matchOrCreateChild(Node base, String name, Map<String, String> attributes) {
        return matchOrCreateChild(base, name, attributes, Map.of());
    }

    // A new child gets the defaults too; an existing one is left as it is.
    private static Node matchOrCreateChild(
            Node base, String name, Map<String, String> attributes, Map<String, String> defaults) {
        return matchChild(base, name, attributes).orElseGet(() -> {
            // The node keeps this map and the callers put values in it, so it has to be mutable.
            var newAttributes = new LinkedHashMap<>(attributes);
            newAttributes.putAll(defaults);
            return base.appendNode(name, newAttributes);
        });
    }

    private static Optional<Node> matchChild(Node base, String name, Map<String, String> attributes) {
        // The children with that name, in a NodeList.
        var children = (List<?>) base.get(name);
        return children.stream()
                .map(Node.class::cast)
                .filter(child -> attributes(child).entrySet().containsAll(attributes.entrySet()))
                .findFirst();
    }

    // Node.attributes() is a raw Map.
    private static Map<Object, Object> attributes(Node node) {
        return node.attributes();
    }

    private ConfigureJavaFormatterXml() {}
}
