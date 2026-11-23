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

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import org.w3c.dom.CDATASection;
import org.w3c.dom.Comment;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.ProcessingInstruction;
import org.w3c.dom.Text;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Edits the XML files in .idea with the JDK's DOM. The plugin does not declare Groovy, so Groovy's XmlParser would be
 * whichever the build's Gradle bundles: Groovy 4 moved it out of groovy.util.
 */
public class XmlUtils {

    // .idea files have no DTD, and the parsers read none: Groovy's XmlParser did not either.
    private static final String DISALLOW_DOCTYPE = "http://apache.org/xml/features/disallow-doctype-decl";

    // The user data of an element that holds the names of its attributes, in the order of the file.
    private static final String ATTRIBUTE_ORDER = "open-java-format.attributeOrder";

    public static void updateIdeaXmlFile(File configurationFile, Consumer<Element> configure) {
        if (!configurationFile.isFile()) {
            var document = newDocumentBuilder().newDocument();
            var project = document.createElement("project");
            project.setAttribute("version", "4");
            document.appendChild(project);
            configure.accept(project);
            write(configurationFile, print(document));
            return;
        }

        Document document;
        try {
            document = parse(Files.readAllBytes(configurationFile.toPath()));
        } catch (IOException | SAXException e) {
            throw new RuntimeException("Couldn't parse existing configuration file: " + configurationFile, e);
        }
        var before = print(document);
        configure.accept(document.getDocumentElement());
        var after = print(document);
        // A file with nothing to change keeps its bytes, however it was written.
        if (!after.equals(before)) {
            write(configurationFile, after);
        }
    }

    static Document parse(byte[] xml) throws IOException, SAXException {
        var document = newDocumentBuilder().parse(new ByteArrayInputStream(xml));
        recordAttributeOrder(xml, document);
        return document;
    }

    /**
     * The node as IntelliJ writes it: a file starts with the XML declaration and has no line break at the end, the
     * elements are indented by two spaces, an empty one ends in {@code " />"}, and the attributes keep the order of the
     * file. Text that is more than whitespace comes out as it is, and so does the rest of its element.
     */
    static String print(Node node) {
        var out = new StringBuilder();
        if (node instanceof Document document) {
            out.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
            for (var child : children(document)) {
                out.append('\n');
                print(child, "", out);
            }
        } else {
            print(node, "", out);
        }
        return out.toString();
    }

    static List<Node> children(Node node) {
        var children = node.getChildNodes();
        return IntStream.range(0, children.getLength()).mapToObj(children::item).toList();
    }

    // An element that holds elements alone puts each on a line of its own, in place of the whitespace between them.
    private static void print(Node node, String indent, StringBuilder out) {
        var children = children(node);
        if (!(node instanceof Element element) || children.stream().anyMatch(XmlUtils::isText)) {
            printInline(node, out);
            return;
        }
        var nested = children.stream().filter(child -> !(child instanceof Text)).toList();
        printStartTag(element, out);
        if (nested.isEmpty()) {
            out.append(" />");
            return;
        }
        out.append('>');
        for (var child : nested) {
            out.append('\n').append(indent).append("  ");
            print(child, indent + "  ", out);
        }
        out.append('\n')
                .append(indent)
                .append("</")
                .append(element.getTagName())
                .append('>');
    }

    private static void printInline(Node node, StringBuilder out) {
        switch (node) {
            case Element element -> {
                printStartTag(element, out);
                var children = children(element);
                if (children.isEmpty()) {
                    out.append(" />");
                } else {
                    out.append('>');
                    for (var child : children) {
                        printInline(child, out);
                    }
                    out.append("</").append(element.getTagName()).append('>');
                }
            }
            case CDATASection cdata ->
                out.append("<![CDATA[").append(cdata.getData()).append("]]>");
            case Text text -> out.append(escape(text.getData(), false));
            case Comment comment -> out.append("<!--").append(comment.getData()).append("-->");
            case ProcessingInstruction instruction -> {
                out.append("<?").append(instruction.getTarget());
                if (!instruction.getData().isEmpty()) {
                    out.append(' ').append(instruction.getData());
                }
                out.append("?>");
            }
            default -> throw new IllegalArgumentException("Unexpected XML node: " + node);
        }
    }

    private static void printStartTag(Element element, StringBuilder out) {
        out.append('<').append(element.getTagName());
        for (var name : attributeNames(element)) {
            out.append(' ')
                    .append(name)
                    .append("=\"")
                    .append(escape(element.getAttribute(name), true))
                    .append('"');
        }
    }

    private static boolean isText(Node node) {
        return node instanceof Text text
                && (text instanceof CDATASection || !text.getData().isBlank());
    }

    // Those the file had come in its order, then those added since, by name: the DOM keeps them sorted.
    private static List<String> attributeNames(Element element) {
        var names = new ArrayList<String>();
        if (element.getUserData(ATTRIBUTE_ORDER) instanceof String[] order) {
            for (var name : order) {
                if (element.hasAttribute(name)) {
                    names.add(name);
                }
            }
        }
        var attributes = element.getAttributes();
        for (var i = 0; i < attributes.getLength(); i++) {
            var name = attributes.item(i).getNodeName();
            if (!names.contains(name)) {
                names.add(name);
            }
        }
        return names;
    }

    // What IntelliJ escapes, and a carriage return besides. A line break or a tab in an attribute survives only as a
    // character reference: a parser turns the characters themselves into spaces.
    private static String escape(String value, boolean attribute) {
        var out = new StringBuilder(value.length());
        for (var i = 0; i < value.length(); i++) {
            var c = value.charAt(i);
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\r' -> out.append("&#13;");
                case '\n' -> out.append(attribute ? "&#10;" : "\n");
                case '\t' -> out.append(attribute ? "&#9;" : "\t");
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    // The DOM keeps an element's attributes sorted by name. SAX reads them in the order of the file, and meets the
    // elements in the order getElementsByTagName returns them.
    private static void recordAttributeOrder(byte[] xml, Document document) throws IOException, SAXException {
        var elements = document.getElementsByTagName("*");
        try {
            var factory = SAXParserFactory.newDefaultInstance();
            factory.setFeature(DISALLOW_DOCTYPE, true);
            factory.newSAXParser().parse(new ByteArrayInputStream(xml), new DefaultHandler() {
                private int next;

                @Override
                public void startElement(String uri, String localName, String qName, Attributes attributes) {
                    var order = new String[attributes.getLength()];
                    for (var i = 0; i < order.length; i++) {
                        order[i] = attributes.getQName(i);
                    }
                    elements.item(next++).setUserData(ATTRIBUTE_ORDER, order, null);
                }
            });
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static DocumentBuilder newDocumentBuilder() {
        try {
            var factory = DocumentBuilderFactory.newDefaultInstance();
            factory.setFeature(DISALLOW_DOCTYPE, true);
            var builder = factory.newDocumentBuilder();
            // Throws on a fatal error, as the default does, without printing it to the console first.
            builder.setErrorHandler(new DefaultHandler());
            return builder;
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void write(File configurationFile, String xml) {
        try {
            Files.writeString(configurationFile.toPath(), xml, UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write back to configuration file: " + configurationFile, e);
        }
    }

    private XmlUtils() {}
}
