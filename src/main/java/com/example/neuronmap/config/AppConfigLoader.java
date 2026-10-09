package com.example.neuronmap.config;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;

/**
 * Читає конфігурацію застосунку та перевіряє, чи містить вона допустимі значення.
 */
public final class AppConfigLoader {

    public static final String RESOURCE = "/app-config.xml";

    /**
     * Повертає результат операції «конфігурація».
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    private AppConfigLoader() {
    }

    /**
     * Повертає або знаходить дані, повʼязані з «потрібні дані».
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    public static AppConfig load() {
        InputStream input = AppConfigLoader.class.getResourceAsStream(RESOURCE);
        if (input == null) {
            /**
             * Повертає результат операції «стан виняток».
             *
             * @param RESOURCE значення, що визначає відповідну операцію для цієї операції.
             *
             * @return значення або обʼєкт, визначений описаною операцією.
             */
            throw new IllegalStateException("Missing application configuration: " + RESOURCE);
        }

        try (input) {
            return parse(input);
        } catch (Exception exception) {
            /**
             * Повертає результат операції «стан виняток».
             *
             * @param RESOURCE значення, що визначає відповідну операцію для цієї операції.
             *
             * @param exception помилка, яку потрібно обробити.
             *
             * @return значення або обʼєкт, визначений описаною операцією.
             */
            throw new IllegalStateException(
                    "Cannot load application configuration: " + RESOURCE,
                    exception
            );
        }
    }

    /**
     * Повертає результат операції «відповідну операцію».
     *
     * @param input значення, що визначає вхід для цієї операції.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    static AppConfig parse(InputStream input) throws Exception {
        if (input == null) {
            /**
             * Повертає результат операції «виняток».
             *
             * @return значення або обʼєкт, визначений описаною операцією.
             */
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
            /**
             * Повертає результат операції «виняток».
             *
             * @return значення або обʼєкт, визначений описаною операцією.
             */
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

    /**
     * Повертає результат операції «відповідну операцію».
     *
     * @param parent батьківський графічний вузол.
     *
     * @param name назва або текстове імʼя обʼєкта.
     *
     * @return значення або обʼєкт, визначений описаною операцією.
     */
    private static Element child(Element parent, String name) {
        for (int index = 0; index < parent.getChildNodes().getLength(); index++) {
            if (parent.getChildNodes().item(index) instanceof Element element
                    && name.equals(element.getTagName())) {
                return element;
            }
        }
        /**
         * Повертає результат операції «виняток».
         *
         * @return значення або обʼєкт, визначений описаною операцією.
         */
        throw new IllegalArgumentException("Missing <" + name + "> configuration section");
    }

    /**
     * Повертає результат операції «відповідну операцію».
     *
     * @param element значення, що визначає відповідну операцію для цієї операції.
     *
     * @param name назва або текстове імʼя обʼєкта.
     *
     * @return текстове значення, сформоване або знайдене методом.
     */
    private static String required(Element element, String name) {
        String value = element.getAttribute(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "Missing attribute '" + name + "' in <" + element.getTagName() + ">"
            );
        }
        return value;
    }

    /**
     * Повертає результат операції «відповідну операцію».
     *
     * @param element значення, що визначає відповідну операцію для цієї операції.
     *
     * @param name назва або текстове імʼя обʼєкта.
     *
     * @return числове значення, визначене методом.
     */
    private static double number(Element element, String name) {
        String value = required(element, name);
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException exception) {
            /**
             * Повертає результат операції «виняток».
             *
             * @param exception помилка, яку потрібно обробити.
             *
             * @return значення або обʼєкт, визначений описаною операцією.
             */
            throw new IllegalArgumentException(
                    "Attribute '" + name + "' must be numeric",
                    exception
            );
        }
    }
}
