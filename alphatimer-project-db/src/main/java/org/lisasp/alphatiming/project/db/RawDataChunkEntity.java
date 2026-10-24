package org.lisasp.alphatiming.project.db;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** A chunk of the raw bytes received from the timekeeping system, in arrival order (ordered by id). */
@Entity(name = "RawDataChunkEntity")
@Table(name = "rawdata")
@Getter
@Setter
@NoArgsConstructor
public class RawDataChunkEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDateTime timestamp;

    private byte[] data;

    public RawDataChunkEntity(LocalDateTime timestamp, byte[] data) {
        this.timestamp = timestamp;
        this.data = data;
    }
}
