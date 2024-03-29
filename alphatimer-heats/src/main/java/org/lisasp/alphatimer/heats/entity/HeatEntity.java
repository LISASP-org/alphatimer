package org.lisasp.alphatimer.heats.entity;

import lombok.*;
import org.lisasp.alphatimer.heats.api.enums.HeatStatus;
import org.lisasp.alphatimer.heats.api.HeatDto;
import org.lisasp.alphatimer.heats.api.LaneDto;
import org.lisasp.basics.spring.jpa.TimestampedEntity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;

@Entity(name = "Heat")
@Table(name = "heat")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@RequiredArgsConstructor
public class HeatEntity extends TimestampedEntity {

    @NonNull
    @NotNull
    private String competition;
    @NonNull
    @Min(1)
    private int event;
    @NonNull
    @Min(1)
    private int heat;
    @NonNull
    @NotNull
    @Enumerated(EnumType.STRING)
    private HeatStatus status;
    private LocalDateTime started;
    @NonNull
    @Min(0)
    private int lapCount;
    @OneToMany(mappedBy = "heat", fetch = FetchType.EAGER, orphanRemoval = true, cascade = CascadeType.ALL)
    private List<LaneEntity> lanes;

    public HeatDto toDto() {
        return new HeatDto(competition, event, heat, status, started, lapCount, lanes.stream().map(lane -> lane.toDto()).toArray(LaneDto[]::new));
    }

    public void update(HeatDto heat) {
        status = heat.getStatus();
        started = heat.getStarted();
        lapCount = heat.getLapCount();
    }
}
