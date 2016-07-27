/**
 * Copyright 2016 by moebiusgames.com
 *
 * Be inspired by this source but please don't just copy it ;)
 */
package de.darkblue.json.ws;

/**
 *
 * @author Florian Frankenberger
 */
public class RemoteInvokationException extends Exception {

    public RemoteInvokationException() {
    }

    public RemoteInvokationException(String message) {
        super(message);
    }

    public RemoteInvokationException(String message, Throwable cause) {
        super(message, cause);
    }

}
