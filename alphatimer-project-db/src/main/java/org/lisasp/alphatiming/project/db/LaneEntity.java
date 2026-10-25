package org.lisasp.alphatiming.project.db;

import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.lisasp.alphatiming.heats.LaneStatus;
import org.lisasp.alphatiming.heats.Penalty;

@Entity(name = "LaneEntity")
@Table(name = "lane")
@Getter
@Setter
@NoArgsConstructor
public class LaneEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "heat_id", nullable = false)
    private HeatEntity heat;

    private int number;
    private int timeInMillis;
    private int lap;
    @Convert(converter = EnumNameConverters.LaneStatusConverter.class)
    private LaneStatus status;
    @Convert(converter = EnumNameConverters.PenaltyConverter.class)
    private Penalty penalty;
    private int place;
    private boolean finished;
}
