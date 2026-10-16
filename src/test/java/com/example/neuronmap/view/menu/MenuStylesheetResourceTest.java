package com.example.neuronmap.view.menu;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Перевіряє цілісність обов'язкового CSS-ресурсу, від якого залежить оформлення головного меню. */
final class MenuStylesheetResourceTest {

    /** Переконується, що таблицю стилів меню включено до classpath під очікуваним шляхом. */
    @Test
    void requiredMenuStylesheetIsPackaged() {
        assertNotNull(
                MainMenuView.class.getResource("/menu-enhancements.css"),
                "Обов'язковий ресурс /menu-enhancements.css має бути включений до пакета застосунку"
        );
    }
}
