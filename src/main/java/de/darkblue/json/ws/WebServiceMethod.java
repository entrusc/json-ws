/**
 * Copyright 2016 by moebiusgames.com
 *
 * Be inspired by this source but please don't just copy it ;)
 */
package de.darkblue.json.ws;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 *
 * @author Florian Frankenberger
 */

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface WebServiceMethod {

    String name() default "__default__";

}
