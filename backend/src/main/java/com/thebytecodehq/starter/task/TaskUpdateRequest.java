package com.thebytecodehq.starter.task;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** A full update. {@code version} is what the client last saw, so a concurrent edit is detected, not overwritten. */
public record TaskUpdateRequest(
        @NotBlank @Size(max = 120) String title,
        @Size(max = 2000) String description,
        @NotNull TaskStatus status,
        LocalDate dueDate,
        @NotNull Long version) {
}
