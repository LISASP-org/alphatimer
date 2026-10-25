package org.lisasp.alphatiming.project.db;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.lisasp.alphatimer.api.ares.serial.events.messages.enums.KindOfTime;
import org.lisasp.alphatimer.api.ares.serial.events.messages.enums.MessageType;
import org.lisasp.alphatimer.api.ares.serial.events.messages.enums.RankInfo;
import org.lisasp.alphatimer.api.ares.serial.events.messages.enums.TimeInfo;
import org.lisasp.alphatimer.api.ares.serial.events.messages.enums.TimeMarker;
import org.lisasp.alphatimer.api.ares.serial.events.messages.enums.TimeType;

import java.time.LocalDateTime;

/** An interpreted ARES message (same content as alphatimer's AresMessage). */
@Entity(name = "AresMessageEntity")
@Table(name = "aresmessage")
@Getter
@Setter
@NoArgsConstructor
public class AresMessageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String originalText1;
    @Column(nullable = false)
    private String originalText2;
    @Column(nullable = false)
    private LocalDateTime timestamp;
    @Convert(converter = EnumNameConverters.MessageTypeConverter.class)
    @Column(nullable = false)
    private MessageType messageType;
    @Convert(converter = EnumNameConverters.KindOfTimeConverter.class)
    @Column(nullable = false)
    private KindOfTime kindOfTime;
    @Convert(converter = EnumNameConverters.TimeTypeConverter.class)
    @Column(nullable = false)
    private TimeType timeType;
    @Column(nullable = false)
    private String usedLanes;
    private byte lapCount;
    private short event;
    private byte heat;
    private byte rank;
    @Convert(converter = EnumNameConverters.RankInfoConverter.class)
    @Column(nullable = false)
    private RankInfo rankInfo;
    private byte lane;
    private byte currentLap;
    private int timeInMillis;
    @Convert(converter = EnumNameConverters.TimeInfoConverter.class)
    @Column(nullable = false)
    private TimeInfo timeInfo;
    @Convert(converter = EnumNameConverters.TimeMarkerConverter.class)
    @Column(nullable = false)
    private TimeMarker timeMarker;
    @Column(nullable = false)
    private String competitionKey;
}
