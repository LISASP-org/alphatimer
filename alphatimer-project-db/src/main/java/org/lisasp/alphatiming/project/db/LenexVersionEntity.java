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

/** A version of the LENEX file the recording read, gzip-compressed, in arrival order (ordered by id). */
@Entity(name = "LenexVersionEntity")
@Table(name = "lenexversion")
@Getter
@Setter
@NoArgsConstructor
public class LenexVersionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDateTime timestamp;

    /** false for a version whose results equal those of the version before; only the newest such version is kept */
    private boolean resultsChanged;

    private byte[] data;

    public LenexVersionEntity(LocalDateTime timestamp, boolean resultsChanged, byte[] data) {
        this.timestamp = timestamp;
        this.resultsChanged = resultsChanged;
        this.data = data;
    }
}
