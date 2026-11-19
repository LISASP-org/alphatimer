package org.lisasp.alphatimer.refinedmessages;

import org.lisasp.alphatimer.api.ares.serial.events.messages.enums.KindOfTime;
import org.lisasp.alphatimer.api.ares.serial.events.messages.enums.MessageType;
import org.lisasp.alphatimer.api.ares.serial.events.messages.enums.TimeInfo;
import org.lisasp.alphatimer.api.ares.serial.events.messages.enums.TimeType;
import org.lisasp.alphatimer.api.refinedmessages.accepted.enums.RefinedKindOfTime;
import org.lisasp.alphatimer.api.refinedmessages.accepted.enums.RefinedMessageType;
import org.lisasp.alphatimer.api.refinedmessages.accepted.enums.RefinedTimeType;

class EnumUtils {
    RefinedKindOfTime convertKindOfTime(KindOfTime kindOfTime) {
        switch (kindOfTime) {
            case Finish:
                return RefinedKindOfTime.Finish;
            case SplitTime:
                return RefinedKindOfTime.SplitTime;
        }
        throw new IllegalArgumentException();
    }

    boolean carriesRaceTimes(MessageType messageType) {
        return switch (messageType) {
            case OnLineTime, CurrentRaceResults, CurrentRaceResultsWithBackupTimes, PreviousRaceResults, PreviousRaceResultsWithBackupTimes -> true;
            default -> false;
        };
    }

    RefinedTimeType convertTimeType(TimeInfo timeInfo, TimeType timeType) {
        if (timeInfo == TimeInfo.Manual) {
            return RefinedTimeType.ManualTime;
        }
        if (timeInfo == TimeInfo.Edited) {
            return RefinedTimeType.CorrectedTime;
        }
        return convertTimeType(timeType);
    }

    RefinedTimeType convertTimeType(TimeType timeType) {
        switch (timeType) {
            case ManualTime:
                return RefinedTimeType.ManualTime;
            case CorrectedTime:
                return RefinedTimeType.CorrectedTime;
            case Empty:
                return RefinedTimeType.Normal;
            case DeletedTime:
                return RefinedTimeType.DeletedTime;
        }
        throw new IllegalArgumentException();
    }

    RefinedMessageType convertMessageType(MessageType messageType) {
        switch (messageType) {
            case CurrentRaceResults:
            case CurrentRaceResultsWithBackupTimes:
                return RefinedMessageType.CurrentRaceResult;
            case PreviousRaceResults:
            case PreviousRaceResultsWithBackupTimes:
                return RefinedMessageType.PreviousRaceResult;
            case OnLineTime:
                return RefinedMessageType.Live;
        }
        throw new IllegalArgumentException();
    }

}
