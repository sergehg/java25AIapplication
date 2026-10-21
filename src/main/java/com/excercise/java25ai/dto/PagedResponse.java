package com.excercise.java25ai.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Stable envelope for paged results.
 *
 * <p>Spring Data's {@code Page} is deliberately not serialized directly: its JSON structure is
 * documented as unstable across versions and it exposes {@code Pageable} and {@code Sort}
 * internals that have no business in a public API contract.
 */
public record PagedResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {

    public static <T> PagedResponse<T> from(Page<T> page) {
        return new PagedResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast()
        );
    }
}
