package com.thebytecodehq.starter.task;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** What the API returns. The JPA entity never leaves the service layer. */
public record TaskResponse(
        UUID id,
        String title,
        String description,
        TaskStatus status,
        LocalDate dueDate,
        String createdBy,
        Instant createdAt,
        Instant updatedAt,
        long version) {

    static TaskResponse from(Task task) {
        return new TaskResponse(task.getId(), task.getTitle(), task.getDescription(), task.getStatus(),
                task.getDueDate(), task.getCreatedBy(), task.getCreatedAt(), task.getUpdatedAt(), task.getVersion());
    }
}
