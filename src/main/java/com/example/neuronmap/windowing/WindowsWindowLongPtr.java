package com.example.neuronmap.windowing;

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.Win32Exception;
import com.sun.jna.platform.win32.WinDef.HWND;

/**
 * Централізує виклики {@code SetWindowLongPtr} і коректне визначення помилки за кодом Win32.
 * Нульове попереднє значення є помилкою лише тоді, коли після виклику встановлено ненульовий код помилки.
 */
final class WindowsWindowLongPtr {

    /** Забороняє створення екземплярів класу, який надає лише спільні статичні операції. */
    private WindowsWindowLongPtr() {
    }

    /**
     * Змінює значення атрибута вікна та перевіряє код помилки, не трактуючи валідний нульовий результат як збій.
     *
     * @param window дескриптор вікна, для якого змінюється атрибут.
     * @param index індекс атрибута Win32.
     * @param value нове значення атрибута.
     * @return попереднє значення атрибута.
     * @throws Win32Exception якщо виклик повернув нульове значення й Win32 повідомив про помилку.
     */
    static Pointer set(HWND window, int index, Pointer value) {
        Kernel32.INSTANCE.SetLastError(0);
        Pointer previousValue = User32.INSTANCE.SetWindowLongPtr(window, index, value);
        return validateResult(previousValue, Kernel32.INSTANCE.GetLastError());
    }

    /**
     * Застосовує визначене Win32 правило для відрізнення нульового результату від помилки виклику.
     *
     * @param previousValue значення, повернуте нативною функцією.
     * @param errorCode код помилки, прочитаний одразу після нативного виклику.
     * @return попереднє значення атрибута, зокрема {@code null}, якщо нульовий результат не є помилкою.
     * @throws Win32Exception якщо результат нульовий, а код помилки ненульовий.
     */
    static Pointer validateResult(Pointer previousValue, int errorCode) {
        if (previousValue == null && errorCode != 0) {
            throw new Win32Exception(errorCode);
        }
        return previousValue;
    }

    /** Контракт виклику {@code SetWindowLongPtr}, який дає змогу перевіряти життєвий цикл без Win32. */
    @FunctionalInterface
    interface Setter {

        /**
         * Змінює атрибут вікна, повертаючи попереднє значення або кидаючи виняток у разі помилки Win32.
         *
         * @param window дескриптор вікна.
         * @param index індекс атрибута Win32.
         * @param value нове значення атрибута.
         * @return попереднє значення атрибута.
         */
        Pointer set(HWND window, int index, Pointer value);
    }
}
