package org.lisasp.alphatiming.project.db;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.lisasp.alphatiming.heats.HeatStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity(name = "HeatEntity")
@Table(name = "heat")
@Getter
@Setter
@NoArgsConstructor
public class HeatEntity {

    /** Hibernate's SQLite dialect silently skips unique keys over several columns, so a new file gets it from this statement. */
    static final String UNIQUE_KEY = "create unique index heat_event_heatNumber on heat (event, heatNumber)";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private int event;
    private int heatNumber;
    @Convert(converter = EnumNameConverters.HeatStatusConverter.class)
    private HeatStatus status;
    private LocalDateTime started;
    private LocalDateTime ended;
    /** Added after the first version: null in older project files. */
    private LocalDateTime lastMessage;
    /** Added after the first version: null in older project files. */
    private Integer startTimeOfDayMillis;
    private int lapCount;
    /** Increases with every change, so the most recently changed heat can be found. */
    private long changeSeq;

    @OneToMany(mappedBy = "heat", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("number")
    private List<LaneEntity> lanes = new ArrayList<>();
}
