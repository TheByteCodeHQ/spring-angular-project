package com.thebytecodehq.starter.common;

import java.util.List;
import org.springframework.data.domain.Page;

/** A stable, explicit paging envelope, so the JSON shape does not depend on Spring Data internals. */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages());
    }
}
