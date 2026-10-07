package com.example.neuronmap.config;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;

/** Loads the immutable application configuration from the classpath XML file. */
public final class AppConfigLoader {

    public static final String RESOURCE = "/app-config.xml";

    private AppConfigLoader() {
    }

    public static AppConfig load() {
        InputStream input = AppConfigLoader.class.getResourceAsStream(RESOURCE);
        if (input == null) {
            throw new IllegalStateException("Missing application configuration: " + RESOURCE);
        }

        try (input) {
            return parse(input);
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Cannot load application configuration: " + RESOURCE,
                    exception
            );
        }
    }

    static AppConfig parse(InputStream input) throws Exception {
        if (input == null) {
            throw new IllegalArgumentException("input must not be null");
        }

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);

        Document document = factory.newDocumentBuilder().parse(input);
        Element root = document.getDocumentElement();

        if (root == null || !"app".equals(root.getTagName())) {
            throw new IllegalArgumentException("Root element must be <app>");
        }

        Element window = child(root, "window");
        Element camera = child(root, "camera");
        Element simulation = child(root, "simulation");

        return new AppConfig(
                new AppConfig.Window(
                        required(window, "title"),
                        number(window, "width"),
                        number(window, "height"),
                        required(window, "stateFile")
                ),
                new AppConfig.Camera(
                        number(camera, "zoomFactor")
                ),
                new AppConfig.Simulation(
                        number(simulation, "defaultTickMs"),
                        number(simulation, "minTickMs"),
                        number(simulation, "maxTickMs")
                )
        );
    }

    private static Element child(Element parent, String name) {
        for (int index = 0; index < parent.getChildNodes().getLength(); index++) {
            if (parent.getChildNodes().item(index) instanceof Element element
                    && name.equals(element.getTagName())) {
                return element;
            }
        }
        throw new IllegalArgumentException("Missing <" + name + "> configuration section");
    }

    private static String required(Element element, String name) {
        String value = element.getAttribute(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "Missing attribute '" + name + "' in <" + element.getTagName() + ">"
            );
        }
        return value;
    }

    private static double number(Element element, String name) {
        String value = required(element, name);
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "Attribute '" + name + "' must be numeric",
                    exception
            );
        }
    }
}
