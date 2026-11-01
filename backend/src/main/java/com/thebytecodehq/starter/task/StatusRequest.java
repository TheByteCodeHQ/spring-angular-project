package com.thebytecodehq.starter.task;

import jakarta.validation.constraints.NotNull;

public record StatusRequest(@NotNull TaskStatus status) {
}
