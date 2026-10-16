package com.example.neuronmap.view.menu;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Перевіряє, що допоміжні засоби інспекції подань меню не входять до публічного API застосунку. */
class PackagePrivateTestAccessorsTest {

    /** Перевіряє видимість допоміжних тестових методів, доступних завдяки спільному пакету. */
    @Test
    void testAccessorsAreNotPublicApi() throws NoSuchMethodException {
        assertPackagePrivate(MainMenuView.class, "mainButtonTextsForTest");
        assertPackagePrivate(MainMenuView.class, "languageSelectorForTest");
        assertPackagePrivate(MainMenuView.class, "backButtonForTest");
        assertPackagePrivate(MainMenuView.class, "saveSettingsButtonForTest");
        assertPackagePrivate(MainMenuView.class, "pageTitleForTest");
        assertPackagePrivate(ProjectListView.class, "scrollPaneForTest");
        assertPackagePrivate(ProjectListView.class, "rowsForTest");
        assertPackagePrivate(LanguageSettingsView.class, "languageSelectorForTest");
        assertPackagePrivate(MainMenuHomeView.class, "settingsButtonForTest");
        assertPackagePrivate(MainMenuHomeView.class, "buttonTextsForTest");
    }

    /**
     * Перевіряє, що метод має доступ лише на рівні пакета, а не публічний, захищений чи приватний доступ.
     *
     * @param owner клас, що оголошує допоміжний метод.
     * @param methodName назва методу без параметрів.
     * @throws NoSuchMethodException якщо очікуваний метод відсутній.
     */
    private static void assertPackagePrivate(Class<?> owner, String methodName) throws NoSuchMethodException {
        Method method = owner.getDeclaredMethod(methodName);
        assertEquals(0, method.getModifiers(),
                owner.getSimpleName() + "." + methodName + " має бути package-private");
    }
}
