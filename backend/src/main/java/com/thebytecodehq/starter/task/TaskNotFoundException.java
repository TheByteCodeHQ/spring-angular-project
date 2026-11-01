package com.thebytecodehq.starter.task;

import java.util.UUID;

public class TaskNotFoundException extends RuntimeException {

    public TaskNotFoundException(UUID id) {
        super("Task " + id + " was not found");
    }
}
