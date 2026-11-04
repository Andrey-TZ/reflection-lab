package org.example.generator;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.FIELD, ElementType.CONSTRUCTOR})
public @interface Generatable {

    /**
     * Явный список классов-реализаций для интерфейсов и абстрактных классов.
     * Если указано несколько, генератор выберет случайную из них.
     */
    Class<?>[] implementations() default {};

    /**
     * Количество элементов при генерации коллекций, мап и массивов,
     * либо длина генерируемой строки (если аннотация висит на поле).
     */
    int size() default 3;

    /**
     * Диапазон значений для числовых полей (int, long, double и т.д.).
     */
    int min() default 1;
    int max() default 1000;

    /**
     * Позволяет исключить конкретное поле из генерации (оставить дефолтным/null).
     */
    boolean ignore() default false;

    /**
     * Конструктор для использования по умолчанию при генерации
     */
    boolean primalConstructor() default false;
}
