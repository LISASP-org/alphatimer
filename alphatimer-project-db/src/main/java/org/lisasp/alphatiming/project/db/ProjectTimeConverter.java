package org.lisasp.alphatiming.project.db;

import jakarta.persistence.AttributeConverter;
import lombok.RequiredArgsConstructor;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** Time stamps are stored as epoch milliseconds; the project's zone, not the JVM's, maps them to local times. */
@RequiredArgsConstructor
final class ProjectTimeConverter implements AttributeConverter<LocalDateTime, Long> {

    private final ZoneId zone;

    @Override
    public Long convertToDatabaseColumn(LocalDateTime time) {
        return time == null ? null : time.atZone(zone).toInstant().toEpochMilli();
    }

    @Override
    public LocalDateTime convertToEntityAttribute(Long millis) {
        return millis == null ? null : LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), zone);
    }
}
