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
@Target(ElementType.TYPE)
public @interface WebService {

    String path() default "/";

}
