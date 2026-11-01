package com.thebytecodehq.starter.task;

public class StaleVersionException extends RuntimeException {

    public StaleVersionException() {
        super("The task was changed by someone else. Reload it and try again.");
    }
}
