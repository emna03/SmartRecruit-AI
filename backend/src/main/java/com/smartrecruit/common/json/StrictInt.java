package com.smartrecruit.common.json;

import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import tools.jackson.databind.annotation.JsonDeserialize;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Entier JSON strict : {@code 2.5}, {@code "5"} ou {@code true} sont refusés (400) au lieu d'être convertis
 * silencieusement (comportement par défaut de Jackson).
 */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@JacksonAnnotationsInside
@JsonDeserialize(using = StrictDeserializers.IntegerOnly.class)
public @interface StrictInt {
}
