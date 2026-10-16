package com.example.neuronmap.view.menu;

import com.example.neuronmap.i18n.SupportedLanguage;
import javafx.event.ActionEvent;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;

import java.util.Objects;

/** Надає тестам інших пакетів контрольований доступ до елементів меню без розширення production API. */
public final class MainMenuViewTestAccess {

    /** Забороняє створення об'єктів допоміжного класу, що містить лише статичні тестові операції. */
    private MainMenuViewTestAccess() {
    }

    /** Імітує натискання кнопки збереження в межах JavaFX-тесту.
     *
     * @param view подання меню, кнопку якого потрібно активувати.
     * @throws NullPointerException якщо подання не задано.
     */
    public static void fireSaveSettings(MainMenuView view) {
        saveSettingsButton(view).fire();
    }

    /** Повертає видимий стан кнопки збереження після застосування чи відкату налаштувань.
     *
     * @param view подання меню, стан якого перевіряється.
     * @return {@code true}, якщо кнопка збереження відображається.
     * @throws NullPointerException якщо подання не задано.
     */
    public static boolean isSaveSettingsVisible(MainMenuView view) {
        return saveSettingsButton(view).isVisible();
    }

    /** Імітує натискання кнопки повернення для перевірки навігації контролера.
     *
     * @param view подання меню, кнопку якого потрібно активувати.
     * @throws NullPointerException якщо подання не задано.
     */
    public static void fireBack(MainMenuView view) {
        Objects.requireNonNull(view, "view").backButtonForTest().fire();
    }

    /** Вибирає мову та передає її події так само, як це робить обробник ComboBox у JavaFX.
     *
     * @param view подання меню з доступним компонентом вибору мови.
     * @param language мова, яку потрібно вибрати.
     * @throws NullPointerException якщо подання або мову не задано.
     */
    public static void chooseLanguage(MainMenuView view, SupportedLanguage language) {
        ComboBox<SupportedLanguage> selector = Objects.requireNonNull(view, "view").languageSelectorForTest();
        selector.setValue(Objects.requireNonNull(language, "language"));
        selector.getOnAction().handle(new ActionEvent(selector, null));
    }

    /** Повертає кнопку збереження після перевірки подання, не відкриваючи цей вузол у production API.
     *
     * @param view подання меню, у якому розташована кнопка.
     * @return кнопка збереження налаштувань.
     * @throws NullPointerException якщо подання не задано.
     */
    private static Button saveSettingsButton(MainMenuView view) {
        return Objects.requireNonNull(view, "view").saveSettingsButtonForTest();
    }
}
