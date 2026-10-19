package com.example.neuronmap.config;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/**
 * Зчитує XML-конфігурацію застосунку, перевіряє обов’язкові значення й перетворює числові параметри на типізовані налаштування.
 */
public final class AppConfigLoader {

    public static final String RESOURCE = "/app-config.xml";

    /** Не дозволяє створювати екземпляри утиліти завантаження конфігурації. */
    private AppConfigLoader() {
    }

    /**
     * Завантажує конфігурацію з вбудованого ресурсу застосунку та розрізняє етапи можливих помилок.
     *
     * @return перевірена типізована конфігурація застосунку.
     * @throws IllegalStateException якщо ресурс відсутній, не читається, містить некоректний XML або значення.
     */
    public static AppConfig load() {
        InputStream input = AppConfigLoader.class.getResourceAsStream(RESOURCE);
        if (Objects.isNull(input)) {
            throw new IllegalStateException("Missing application configuration: " + RESOURCE);
        }

        try (input) {
            return parse(input);
        } catch (ParserConfigurationException exception) {
            throw new IllegalStateException(
                    "Cannot configure secure XML parser for application configuration: " + RESOURCE,
                    exception
            );
        } catch (SAXException exception) {
            throw new IllegalStateException(
                    "Cannot parse application configuration XML: " + RESOURCE,
                    exception
            );
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Cannot read application configuration: " + RESOURCE,
                    exception
            );
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "Invalid application configuration structure or values: " + RESOURCE,
                    exception
            );
        }
    }

    /**
     * Розбирає XML-потік, перевіряє структуру документа й створює типізовану конфігурацію.
     * XML-парсер не завантажує зовнішні сутності й DTD та не розгортає XInclude.
     *
     * @param input відкритий потік XML-конфігурації; метод не закриває потік, бо його власник визначає життєвий цикл.
     * @return конфігурація з параметрами вікна, камери та симуляції.
     * @throws IllegalArgumentException якщо потік не задано або структура/значення конфігурації некоректні.
     * @throws ParserConfigurationException якщо XML-парсер неможливо налаштувати із заданими обмеженнями безпеки.
     * @throws SAXException якщо XML-документ має синтаксичну помилку або порушує обмеження парсера.
     * @throws IOException якщо під час читання XML-потоку сталася помилка введення-виведення.
     */
    static AppConfig parse(InputStream input)
            throws ParserConfigurationException, SAXException, IOException {
        if (Objects.isNull(input)) {
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

        if (Objects.isNull(root) || !"app".equals(root.getTagName())) {
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
     * Знаходить безпосередній дочірній XML-елемент за назвою.
     *
     * @param parent батьківський елемент, серед безпосередніх дочірніх вузлів якого шукають секцію.
     * @param name точна назва обов’язкового дочірнього елемента.
     * @return знайдений дочірній елемент.
     * @throws IllegalArgumentException якщо дочірній елемент із заданою назвою відсутній.
     */
    private static Element child(Element parent, String name) {
        for (int index = 0; index < parent.getChildNodes().getLength(); index++) {
            if (parent.getChildNodes().item(index) instanceof Element element
                    && name.equals(element.getTagName())) {
                return element;
            }
        }

        throw new IllegalArgumentException("Missing <" + name + "> configuration section");
    }

    /**
     * Читає непорожнє обов’язкове значення атрибута XML-елемента.
     *
     * @param element XML-елемент, у якому перевіряють атрибут.
     * @param name назва обов’язкового атрибута.
     * @return значення атрибута без додаткового перетворення.
     * @throws IllegalArgumentException якщо атрибут відсутній або містить лише пробіли.
     */
    private static String required(Element element, String name) {
        String value = element.getAttribute(name);
        if (Objects.isNull(value) || value.isBlank()) {
            throw new IllegalArgumentException(
                    "Missing attribute '" + name + "' in <" + element.getTagName() + ">"
            );
        }
        return value;
    }

    /**
     * Читає обов’язковий атрибут XML-елемента та перетворює його на число подвійної точності.
     *
     * @param element XML-елемент, у якому міститься числовий атрибут.
     * @param name назва атрибута, значення якого потрібно перетворити.
     * @return числове значення атрибута.
     * @throws IllegalArgumentException якщо атрибут відсутній, порожній або не є числом.
     */
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
