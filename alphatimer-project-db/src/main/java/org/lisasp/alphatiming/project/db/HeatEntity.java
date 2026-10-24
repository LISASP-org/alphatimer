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
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.lisasp.alphatiming.heats.HeatStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity(name = "HeatEntity")
@Table(name = "heat", uniqueConstraints = @UniqueConstraint(columnNames = {"competition", "event", "heatNumber"}))
@Getter
@Setter
@NoArgsConstructor
public class HeatEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String competition;
    private int event;
    private int heatNumber;
    @Convert(converter = EnumNameConverters.HeatStatusConverter.class)
    private HeatStatus status;
    private LocalDateTime started;
    private LocalDateTime ended;
    /** Added after the first version: null in older project files. */
    private LocalDateTime lastMessage;
    private int lapCount;
    /** Increases with every change, so the most recently changed heat can be found. */
    private long changeSeq;

    @OneToMany(mappedBy = "heat", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("number")
    private List<LaneEntity> lanes = new ArrayList<>();
}
