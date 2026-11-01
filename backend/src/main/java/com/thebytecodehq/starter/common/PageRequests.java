package com.thebytecodehq.starter.common;

import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public final class PageRequests {

    public static final int MAX_PAGE_SIZE = 100;

    private PageRequests() {
    }

    /**
     * Builds a bounded page request. Sorting is limited to an allow-list, because a client-chosen sort
     * property otherwise reaches the query layer.
     */
    public static Pageable of(int page, int size, String sort, Set<String> allowedProperties, Sort fallback) {
        if (page < 0) {
            throw new InvalidRequestException("page must be 0 or greater");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidRequestException("size must be between 1 and " + MAX_PAGE_SIZE);
        }
        if (sort == null || sort.isBlank()) {
            return PageRequest.of(page, size, fallback);
        }
        String[] parts = sort.split(",", -1);
        String property = parts[0].strip();
        if (!allowedProperties.contains(property)) {
            throw new InvalidRequestException("Cannot sort by '" + property + "'. Allowed: "
                    + String.join(", ", allowedProperties.stream().sorted().toList()));
        }
        String direction = parts.length > 1 ? parts[1].strip().toLowerCase() : "asc";
        if (parts.length > 2 || !(direction.equals("asc") || direction.equals("desc"))) {
            throw new InvalidRequestException("sort must look like 'property,asc' or 'property,desc'");
        }
        // id as a tie-breaker keeps paging stable when many rows share the sort value
        Sort order = Sort.by(direction.equals("desc") ? Sort.Direction.DESC : Sort.Direction.ASC, property)
                .and(Sort.by("id"));
        return PageRequest.of(page, size, order);
    }
}
