package org.lisasp.alphatiming.project.db;

import org.lisasp.alphatimer.api.ares.serial.events.messages.enums.KindOfTime;
import org.lisasp.alphatimer.api.ares.serial.events.messages.enums.MessageType;
import org.lisasp.alphatimer.api.ares.serial.events.messages.enums.RankInfo;
import org.lisasp.alphatimer.api.ares.serial.events.messages.enums.TimeInfo;
import org.lisasp.alphatimer.api.ares.serial.events.messages.enums.TimeMarker;
import org.lisasp.alphatimer.api.ares.serial.events.messages.enums.TimeType;
import org.lisasp.alphatiming.heats.HeatStatus;
import org.lisasp.alphatiming.heats.LaneStatus;
import org.lisasp.alphatiming.heats.Penalty;

final class EnumNameConverters {

    private EnumNameConverters() {
    }

    public static final class HeatStatusConverter extends EnumNameConverter<HeatStatus> {
        public HeatStatusConverter() {
            super(HeatStatus.class);
        }
    }

    public static final class LaneStatusConverter extends EnumNameConverter<LaneStatus> {
        public LaneStatusConverter() {
            super(LaneStatus.class);
        }
    }

    public static final class PenaltyConverter extends EnumNameConverter<Penalty> {
        public PenaltyConverter() {
            super(Penalty.class);
        }
    }

    public static final class LegacyLaneStatusConverter extends EnumNameConverter<org.lisasp.alphatiming.legacy.server.dto.LaneStatus> {
        public LegacyLaneStatusConverter() {
            super(org.lisasp.alphatiming.legacy.server.dto.LaneStatus.class);
        }
    }

    public static final class MessageTypeConverter extends EnumNameConverter<MessageType> {
        public MessageTypeConverter() {
            super(MessageType.class);
        }
    }

    public static final class KindOfTimeConverter extends EnumNameConverter<KindOfTime> {
        public KindOfTimeConverter() {
            super(KindOfTime.class);
        }
    }

    public static final class TimeTypeConverter extends EnumNameConverter<TimeType> {
        public TimeTypeConverter() {
            super(TimeType.class);
        }
    }

    public static final class RankInfoConverter extends EnumNameConverter<RankInfo> {
        public RankInfoConverter() {
            super(RankInfo.class);
        }
    }

    public static final class TimeInfoConverter extends EnumNameConverter<TimeInfo> {
        public TimeInfoConverter() {
            super(TimeInfo.class);
        }
    }

    public static final class TimeMarkerConverter extends EnumNameConverter<TimeMarker> {
        public TimeMarkerConverter() {
            super(TimeMarker.class);
        }
    }
}
