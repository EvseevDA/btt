package com.github.evseevda.domainlogicmodule.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/** Передаёт произвольное смещение API в запрос Spring Data. */
public record OffsetPage(long offset, int size) implements Pageable {
    /** Проверяет границы смещения и размера. */
    public OffsetPage {
        if (offset < 0 || size < 1) {
            throw new IllegalArgumentException(
                    "Смещение должно быть неотрицательным, размер страницы — положительным");
        }
    }

    /** Возвращает номер страницы, содержащей смещение. */
    @Override
    public int getPageNumber() {
        return Math.toIntExact(offset / size);
    }

    /** Возвращает размер страницы. */
    @Override
    public int getPageSize() {
        return size;
    }

    /** Возвращает точное смещение. */
    @Override
    public long getOffset() {
        return offset;
    }

    /** Использует порядок, указанный в запросе репозитория. */
    @Override
    public Sort getSort() {
        return Sort.unsorted();
    }

    /** Сдвигает окно на размер страницы вперёд. */
    @Override
    public Pageable next() {
        return new OffsetPage(offset + size, size);
    }

    /** Сдвигает окно на размер страницы назад до начала выборки. */
    @Override
    public Pageable previousOrFirst() {
        return new OffsetPage(Math.max(0, offset - size), size);
    }

    /** Возвращает страницу с нулевым смещением. */
    @Override
    public Pageable first() {
        return new OffsetPage(0, size);
    }

    /** Возвращает страницу по её номеру. */
    @Override
    public Pageable withPage(int pageNumber) {
        return new OffsetPage((long) pageNumber * size, size);
    }

    /** Проверяет, можно ли сдвинуть выборку назад. */
    @Override
    public boolean hasPrevious() {
        return offset > 0;
    }
}
