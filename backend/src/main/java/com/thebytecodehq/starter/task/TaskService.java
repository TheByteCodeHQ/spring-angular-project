package com.thebytecodehq.starter.task;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TaskService {

    private final TaskRepository repository;

    public TaskService(TaskRepository repository) {
        this.repository = repository;
    }

    public Page<TaskResponse> search(TaskStatus status, String text, Pageable pageable) {
        return repository.findAll(matching(status, text), pageable).map(TaskResponse::from);
    }

    public TaskResponse get(UUID id) {
        return TaskResponse.from(find(id));
    }

    @Transactional
    public TaskResponse create(TaskRequest request) {
        Task task = new Task(request.title().strip(), clean(request.description()),
                request.status() == null ? TaskStatus.TODO : request.status(), request.dueDate());
        return TaskResponse.from(repository.saveAndFlush(task));
    }

    @Transactional
    public TaskResponse update(UUID id, TaskUpdateRequest request) {
        Task task = find(id);
        if (!task.getVersion().equals(request.version())) {
            throw new StaleVersionException();
        }
        task.update(request.title().strip(), clean(request.description()), request.status(), request.dueDate());
        return TaskResponse.from(repository.saveAndFlush(task));
    }

    @Transactional
    public TaskResponse changeStatus(UUID id, TaskStatus status) {
        Task task = find(id);
        task.changeStatus(status);
        return TaskResponse.from(repository.saveAndFlush(task));
    }

    @Transactional
    public void delete(UUID id) {
        repository.delete(find(id));
    }

    private Task find(UUID id) {
        return repository.findById(id).orElseThrow(() -> new TaskNotFoundException(id));
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static Specification<Task> matching(TaskStatus status, String text) {
        return (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (text != null && !text.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("title")), "%" + escapeLike(text.strip().toLowerCase()) + "%", '\\'));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /** A user's search text is data: '%' and '_' must not act as wildcards. */
    static String escapeLike(String raw) {
        return raw.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
