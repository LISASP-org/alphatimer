package org.lisasp.alphatiming.project.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MessageType messageType;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private KindOfTime kindOfTime;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TimeType timeType;
    @Column(nullable = false)
    private String usedLanes;
    private byte lapCount;
    private short event;
    private byte heat;
    private byte rank;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RankInfo rankInfo;
    private byte lane;
    private byte currentLap;
    private int timeInMillis;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TimeInfo timeInfo;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TimeMarker timeMarker;
    @Column(nullable = false)
    private String competitionKey;
}
