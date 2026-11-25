package org.lisasp.alphatiming.project.db;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.lisasp.alphatimer.api.ares.serial.events.messages.DataHandlingMessage;
import org.lisasp.alphatimer.api.ares.serial.events.messages.enums.KindOfTime;
import org.lisasp.alphatimer.api.ares.serial.events.messages.enums.MessageType;
import org.lisasp.alphatimer.api.ares.serial.events.messages.enums.RankInfo;
import org.lisasp.alphatimer.api.ares.serial.events.messages.enums.TimeInfo;
import org.lisasp.alphatimer.api.ares.serial.events.messages.enums.TimeMarker;
import org.lisasp.alphatimer.api.ares.serial.events.messages.enums.TimeType;
import org.lisasp.alphatiming.project.db.testdata.DataHandlingMessageTestData;

import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class MessageStoreTest {

    @TempDir
    Path dir;

    private static DataHandlingMessage message(String competition, short event, byte heat) {
        return message(competition, event, heat, LocalDateTime.of(2021, 6, 21, 14, 53));
    }

    static DataHandlingMessage message(String competition, short event, byte heat, LocalDateTime timestamp) {
        return new DataHandlingMessage(timestamp,
                                       competition,
                                       new String(DataHandlingMessageTestData.message1),
                                       new String(DataHandlingMessageTestData.message2),
                                       MessageType.OnLineTime,
                                       KindOfTime.Start,
                                       TimeType.Empty,
                                       DataHandlingMessageTestData.createUsedLanes(),
                                       (byte) 2,
                                       event,
                                       heat,
                                       (byte) 0,
                                       RankInfo.Normal,
                                       (byte) 1,
                                       (byte) 0,
                                       112853930,
                                       TimeInfo.Normal,
                                       TimeMarker.Empty);
    }

    @Test
    void storesAndFindsByCompetitionEventAndHeat() throws Exception {
        try (TimingDatabase db = TimingDatabase.createNew(dir)) {
            MessageStore store = new MessageStore(db);
            DataHandlingMessage m = message("TestWK", (short) 1, (byte) 1);
            store.put(m);
            store.put(message("Other", (short) 1, (byte) 1));
            store.put(message("TestWK", (short) 2, (byte) 1));

            assertThat(store.findBy("TestWK", (short) 1, (byte) 1)).containsExactly(m);
            assertThat(store.findBy("TestWK", (short) 1, (byte) 2)).isEmpty();
            assertThat(store.findBy("Nope", (short) 1, (byte) 1)).isEmpty();
            db.closeAndDelete();
        }
    }

    @Test
    void findAllReturnsEveryMessageInTheOrderItWasStored() throws Exception {
        try (TimingDatabase db = TimingDatabase.createNew(dir)) {
            MessageStore store = new MessageStore(db);
            DataHandlingMessage first = message("TestWK", (short) 1, (byte) 2);
            DataHandlingMessage second = message("Other", (short) 1, (byte) 1);
            DataHandlingMessage third = message("TestWK", (short) 1, (byte) 1);
            store.put(first);
            store.put(second);
            store.put(third);

            assertThat(store.findAll()).containsExactly(first, second, third);
            db.closeAndDelete();
        }
    }

    @Test
    void theFormerNameOfTheBackupTimeTypeIsStillRead() throws Exception {
        try (TimingDatabase db = TimingDatabase.createNew(dir)) {
            MessageStore store = new MessageStore(db);
            store.put(message("TestWK", (short) 1, (byte) 1));
            db.write(em -> em.createNativeQuery("update aresmessage set messageType = 'UnknownValue7'").executeUpdate());

            assertThat(store.findBy("TestWK", (short) 1, (byte) 1)).extracting(DataHandlingMessage::getMessageType)
                                                                  .containsExactly(MessageType.BackupTime);
            db.closeAndDelete();
        }
    }

    @Test
    void theFormerNameOfTheBackupKindOfTimeIsStillRead() throws Exception {
        try (TimingDatabase db = TimingDatabase.createNew(dir)) {
            MessageStore store = new MessageStore(db);
            store.put(message("TestWK", (short) 1, (byte) 1));
            db.write(em -> em.createNativeQuery("update aresmessage set kindOfTime = 'UnknownValueB'").executeUpdate());

            assertThat(store.findBy("TestWK", (short) 1, (byte) 1)).extracting(DataHandlingMessage::getKindOfTime)
                                                                  .containsExactly(KindOfTime.Backup);
            db.closeAndDelete();
        }
    }

    @Test
    void theFormerNameOfTheStartingBlockRankInfoIsStillRead() throws Exception {
        try (TimingDatabase db = TimingDatabase.createNew(dir)) {
            MessageStore store = new MessageStore(db);
            store.put(message("TestWK", (short) 1, (byte) 1));
            db.write(em -> em.createNativeQuery("update aresmessage set rankInfo = 'Disqualified'").executeUpdate());

            assertThat(store.findBy("TestWK", (short) 1, (byte) 1)).extracting(DataHandlingMessage::getRankInfo)
                                                                  .containsExactly(RankInfo.StartingBlock);
            db.closeAndDelete();
        }
    }
}
