package com.example.neuronmap.window;

/**
 * Платформонезалежний контракт взаємодії адаптера ОС із власним оформленням вікна.
 * Контракт передає лише логічну геометрію та команди, не відкриваючи деталей JavaFX або нативних API.
 */
public interface WindowChrome {

    /**
     * Повертає останній узгоджений знімок геометрії для перевірки інтерактивної області.
     *
     * @return геометрія або {@code null}, доки компонування подання ще не завершилося.
     */
    WindowHitTestCalculator.Layout hitTestLayout();

    /**
     * Застосовує виміряні системою відступи зон зміни розміру.
     *
     * @param horizontalBorder горизонтальний відступ у логічних пікселях.
     * @param verticalBorder вертикальний відступ у логічних пікселях.
     * @throws IllegalArgumentException якщо відступи не є скінченними додатними числами.
     */
    void setNativeResizeBorders(double horizontalBorder, double verticalBorder);

    /**
     * Виконує дію кнопки заголовка після завершення нативного кліку.
     *
     * @param target кнопка, на якій завершився клік.
     */
    void performNativeButtonAction(WindowHitTestCalculator.HitTarget target);

    /** Перемикає максимізацію після подвійного клацання по вільній області заголовка. */
    void performNativeCaptionDoubleClick();

    /**
     * Оновлює стан наведення на кнопку заголовка.
     *
     * @param target кнопка під курсором або {@code null}, якщо курсор залишив кнопки.
     */
    void setNativeHoveredControl(WindowHitTestCalculator.HitTarget target);

    /**
     * Оновлює стан натискання кнопки заголовка.
     *
     * @param target натиснута кнопка або {@code null}, якщо натискання завершено.
     */
    void setNativePressedControl(WindowHitTestCalculator.HitTarget target);
}
