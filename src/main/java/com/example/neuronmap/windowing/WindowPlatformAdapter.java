package com.example.neuronmap.windowing;

import com.example.neuronmap.view.window.WindowChromeView;
import javafx.stage.Stage;

/**
 * Інтегрує власне оформлення головного вікна з механізмами керування вікнами конкретної операційної системи.
 * Реалізації можуть використовувати нативні API або платформний резервний механізм, не змінюючи контролер вікна.
 */
public interface WindowPlatformAdapter {

    /**
     * Під'єднує обробку переміщення, зміни розміру та системних команд до вже показаного вікна.
     *
     * @param stage головне JavaFX-вікно.
     * @param chromeView власне подання заголовка та вмісту вікна.
     * @throws RuntimeException якщо платформну інтеграцію неможливо встановити.
     */
    void install(Stage stage, WindowChromeView chromeView);

    /**
     * Від'єднує нативні обробники й звільняє ресурси адаптера.
     * Повторний виклик має бути безпечним.
     */
    void dispose();
}
