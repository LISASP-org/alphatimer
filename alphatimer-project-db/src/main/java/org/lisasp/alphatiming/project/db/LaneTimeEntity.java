package org.lisasp.alphatiming.project.db;

import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.lisasp.alphatiming.legacy.api.LaneStatus;

import java.time.LocalDateTime;

/** One time of one lane, in the form the legacy (AlphaServer) endpoints need it. */
@Entity(name = "LaneTimeEntity")
@Table(name = "lanetime")
@Getter
@Setter
@NoArgsConstructor
public class LaneTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String competition;
    private int event;
    private int heatNumber;
    private int laneIndex;
    private long timeInMillis;
    @Convert(converter = EnumNameConverters.LegacyLaneStatusConverter.class)
    private LaneStatus status;
    private LocalDateTime timestamp;
}
