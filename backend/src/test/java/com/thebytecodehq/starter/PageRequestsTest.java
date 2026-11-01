package com.thebytecodehq.starter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thebytecodehq.starter.common.InvalidRequestException;
import com.thebytecodehq.starter.common.PageRequests;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.Sort;

class PageRequestsTest {

    private static final Set<String> ALLOWED = Set.of("title", "createdAt");
    private static final Sort FALLBACK = Sort.by(Sort.Direction.DESC, "createdAt");

    @Test
    void usesTheFallbackSortWhenNoneIsGiven() {
        var pageable = PageRequests.of(2, 10, null, ALLOWED, FALLBACK);
        assertThat(pageable.getPageNumber()).isEqualTo(2);
        assertThat(pageable.getPageSize()).isEqualTo(10);
        assertThat(pageable.getSort()).isEqualTo(FALLBACK);
    }

    @Test
    void addsAnIdTieBreakerSoPagingIsStable() {
        var sort = PageRequests.of(0, 10, "title,desc", ALLOWED, FALLBACK).getSort();
        assertThat(sort.toList()).extracting(o -> o.getProperty() + ":" + o.getDirection())
                .containsExactly("title:DESC", "id:ASC");
    }

    @Test
    void defaultsToAscending() {
        assertThat(PageRequests.of(0, 10, "title", ALLOWED, FALLBACK).getSort().getOrderFor("title").isAscending()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"password,asc", "title,sideways", "title,asc,extra", "id", "title;drop table,asc", ",asc"})
    void rejectsUnknownPropertiesAndBadDirections(String sort) {
        assertThatThrownBy(() -> PageRequests.of(0, 10, sort, ALLOWED, FALLBACK)).isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void enforcesPageBounds() {
        assertThatThrownBy(() -> PageRequests.of(-1, 10, null, ALLOWED, FALLBACK)).isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> PageRequests.of(0, 0, null, ALLOWED, FALLBACK)).isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> PageRequests.of(0, 101, null, ALLOWED, FALLBACK)).isInstanceOf(InvalidRequestException.class);
        assertThat(PageRequests.of(0, 100, null, ALLOWED, FALLBACK).getPageSize()).isEqualTo(100);
    }
}
