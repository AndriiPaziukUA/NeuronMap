package com.example.neuronmap.observability;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Позначає метод як самостійну прикладну дію з окремим ідентифікатором і повним деревом викликів. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface TraceAction {

    /**
     * Повертає зрозумілу назву дії, яка потрапить до стартового й завершального рядків журналу.
     *
     * @return коротка назва прикладної дії.
     */
    String value();
}
