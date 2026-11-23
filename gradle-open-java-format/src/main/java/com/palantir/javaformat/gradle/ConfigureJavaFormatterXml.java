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

import java.lang.module.ModuleDescriptor;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.w3c.dom.Element;

final class ConfigureJavaFormatterXml {

    static void configureJavaFormat(Element rootNode, List<URI> uris) {
        var settings = matchOrCreateChild(rootNode, "component", Map.of("name", "PalantirJavaFormatSettings"));
        // enable
        matchOrCreateChild(settings, "option", Map.of("name", "enabled")).setAttribute("value", "true");
        // configure classpath
        var classPath = matchOrCreateChild(settings, "option", Map.of("name", "implementationClassPath"));
        var listItems = matchOrCreateChild(classPath, "list", Map.of());
        for (var child : XmlUtils.children(listItems)) {
            listItems.removeChild(child);
        }
        for (var uri : uris) {
            appendChild(listItems, "option", Map.of("value", uri.toString()));
        }
        // Earlier versions wrote the native binary for openjavaformat.native.formatter=true. The IDE plugin has a
        // setting of its own for that now, useNativeImage, which is left alone, and no longer reads this option.
        matchChild(settings, "option", Map.of("name", "nativeImageClassPath")).ifPresent(settings::removeChild);
    }

    static void configureWorkspaceXml(Element rootNode) {
        configureFormatOnSave(rootNode);
        configureOptimizeOnSave(rootNode);
    }

    // Lists an IDEA plugin as required by the project, so IntelliJ offers to install or update it. The other
    // required plugins stay, and so does a higher min-version for this one, such as a developer's own choice.
    static void configureExternalDependencies(Element rootNode, String pluginId, String minVersion) {
        var externalDependencies = matchOrCreateChild(rootNode, "component", Map.of("name", "ExternalDependencies"));
        var plugin = matchOrCreateChild(externalDependencies, "plugin", Map.of("id", pluginId));
        // Empty when the plugin is listed without one.
        var existing = plugin.getAttribute("min-version");
        var required = ModuleDescriptor.Version.parse(minVersion);
        if (existing.isEmpty() || ModuleDescriptor.Version.parse(existing).compareTo(required) < 0) {
            plugin.setAttribute("min-version", minVersion);
        }
    }

    private static void configureFormatOnSave(Element rootNode) {
        configureOnSaveAction(matchOrCreateChild(rootNode, "component", Map.of("name", "FormatOnSaveOptions")));
    }

    private static void configureOptimizeOnSave(Element rootNode) {
        configureOnSaveAction(matchOrCreateChild(rootNode, "component", Map.of("name", "OptimizeOnSaveOptions")));
    }

    private static void configureOnSaveAction(Element onSaveOptions) {
        // In IntelliJ <2023.3.7, the myRunOnSave option is default false.
        // In IntelliJ >=2023.3.7, it is default true, and if it is set to true IntelliJ will remove it from the XML
        //     the next time the file is saved or the actions on save options window is saved.
        // So to cover all cases, we always write it out and ensure it is true
        matchOrCreateChild(onSaveOptions, "option", Map.of("name", "myRunOnSave"))
                .setAttribute("value", "true");

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

    private static Element matchOrCreateChild(Element base, String name, Map<String, String> attributes) {
        return matchOrCreateChild(base, name, attributes, Map.of());
    }

    // A new child gets the defaults too; an existing one is left as it is.
    private static Element matchOrCreateChild(
            Element base, String name, Map<String, String> attributes, Map<String, String> defaults) {
        return matchChild(base, name, attributes).orElseGet(() -> {
            var child = appendChild(base, name, attributes);
            defaults.forEach(child::setAttribute);
            return child;
        });
    }

    private static Element appendChild(Element base, String name, Map<String, String> attributes) {
        var child = base.getOwnerDocument().createElement(name);
        attributes.forEach(child::setAttribute);
        base.appendChild(child);
        return child;
    }

    private static Optional<Element> matchChild(Element base, String name, Map<String, String> attributes) {
        return XmlUtils.children(base).stream()
                .filter(Element.class::isInstance)
                .map(Element.class::cast)
                .filter(child -> child.getTagName().equals(name) && hasAttributes(child, attributes))
                .findFirst();
    }

    private static boolean hasAttributes(Element element, Map<String, String> attributes) {
        return attributes.entrySet().stream()
                .allMatch(attribute -> element.hasAttribute(attribute.getKey())
                        && element.getAttribute(attribute.getKey()).equals(attribute.getValue()));
    }

    private ConfigureJavaFormatterXml() {}
}
