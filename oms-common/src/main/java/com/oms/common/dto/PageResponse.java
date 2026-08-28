package com.oms.common.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Serialisation-stable page wrapper. Spring's PageImpl serialises with an unstable
 * JSON shape across versions, so services return this instead.
 */
@Getter
@Setter
@NoArgsConstructor
public class PageResponse<T> {

    private List<T> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean first;
    private boolean last;

    public static <E, D> PageResponse<D> of(Page<E> source, Function<E, D> mapper) {
        PageResponse<D> response = new PageResponse<>();
        response.content = source.getContent().stream().map(mapper).collect(Collectors.toList());
        response.page = source.getNumber();
        response.size = source.getSize();
        response.totalElements = source.getTotalElements();
        response.totalPages = source.getTotalPages();
        response.first = source.isFirst();
        response.last = source.isLast();
        return response;
    }
}
