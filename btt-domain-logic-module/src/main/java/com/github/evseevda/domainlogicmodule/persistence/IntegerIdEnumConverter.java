package com.github.evseevda.domainlogicmodule.persistence;

import jakarta.persistence.AttributeConverter;
import java.util.function.IntFunction;
import java.util.function.ToIntFunction;

/** Хранит перечисление как числовой идентификатор. */
public abstract class IntegerIdEnumConverter<E extends Enum<E>>
        implements AttributeConverter<E, Integer> {

    private final ToIntFunction<E> getId;
    private final IntFunction<E> fromId;

    protected IntegerIdEnumConverter(ToIntFunction<E> getId, IntFunction<E> fromId) {
        this.getId = getId;
        this.fromId = fromId;
    }

    /** Преобразует значение перечисления в идентификатор. */
    @Override
    public final Integer convertToDatabaseColumn(E value) {
        return value == null ? null : getId.applyAsInt(value);
    }

    /** Восстанавливает значение перечисления по идентификатору. */
    @Override
    public final E convertToEntityAttribute(Integer id) {
        return id == null ? null : fromId.apply(id);
    }
}
