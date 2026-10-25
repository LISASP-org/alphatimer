package org.lisasp.alphatiming.project.db;

import jakarta.persistence.AttributeConverter;

/**
 * Stores an enum as its name like {@code @Enumerated(STRING)}, but as a plain column: Hibernate writes a CHECK with the
 * enum's values for {@code @Enumerated}, which no later version could extend in an existing project file.
 */
abstract class EnumNameConverter<E extends Enum<E>> implements AttributeConverter<E, String> {

    private final Class<E> type;

    EnumNameConverter(Class<E> type) {
        this.type = type;
    }

    @Override
    public String convertToDatabaseColumn(E value) {
        return value == null ? null : value.name();
    }

    @Override
    public E convertToEntityAttribute(String name) {
        return name == null ? null : Enum.valueOf(type, name);
    }
}
