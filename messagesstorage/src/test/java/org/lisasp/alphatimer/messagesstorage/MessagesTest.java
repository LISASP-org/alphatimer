package org.lisasp.alphatimer.messagesstorage;

import org.lisasp.alphatimer.api.protocol.DataHandlingMessageRepository;
import org.lisasp.alphatimer.api.protocol.events.messages.DataHandlingMessage;
import org.lisasp.alphatimer.api.protocol.events.messages.enums.*;
import org.lisasp.alphatimer.api.protocol.events.messages.values.UsedLanes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(SpringExtension.class)
@DataJpaTest
class MessagesTest {

    @Autowired
    AresMessageRepository repository;

    private Messages messages;
    private DataHandlingMessage messageHeat1Lane1;
    private DataHandlingMessage messageHeat1Lane2;
    private DataHandlingMessage messageHeat2Lane1;
    private DataHandlingMessage messageHeat2Lane2;

    private final String competitionKey = "TestCompetition";

    private final String messageHeat1Lane1String = "DataHandlingMessage(originalText1=1, originalText2=2, messageType=ReadyToStart, kindOfTime=Empty, timeType=Empty, usedLanes=UsedLanes(1010101010), lapCount=0, event=1, heat=1, rank=0, rankInfo=Normal, lane=1, currentLap=0, timeInMillis=0, timeInfo=Normal, timeMarker=Empty)";
    private final String messageHeat1Lane2String = "DataHandlingMessage(originalText1=1, originalText2=2, messageType=ReadyToStart, kindOfTime=Empty, timeType=Empty, usedLanes=UsedLanes(1010101010), lapCount=0, event=1, heat=1, rank=0, rankInfo=Normal, lane=2, currentLap=0, timeInMillis=0, timeInfo=Normal, timeMarker=Empty)";
    private final String messageHeat2Lane1String = "DataHandlingMessage(originalText1=1, originalText2=2, messageType=ReadyToStart, kindOfTime=Empty, timeType=Empty, usedLanes=UsedLanes(1010101010), lapCount=0, event=1, heat=2, rank=0, rankInfo=Normal, lane=1, currentLap=0, timeInMillis=0, timeInfo=Normal, timeMarker=Empty)";
    private final String messageHeat2Lane2String = "DataHandlingMessage(originalText1=1, originalText2=2, messageType=ReadyToStart, kindOfTime=Empty, timeType=Empty, usedLanes=UsedLanes(1010101010), lapCount=0, event=1, heat=2, rank=0, rankInfo=Normal, lane=2, currentLap=0, timeInMillis=0, timeInfo=Normal, timeMarker=Empty)";

    @BeforeEach
    void prepare() {
        messages = new Messages(repository);
        messageHeat1Lane1 = new DataHandlingMessage(
                "1",
                "2",
                MessageType.ReadyToStart,
                KindOfTime.Empty,
                TimeType.Empty,
                new UsedLanes(getLanes()),
                (byte) 0,
                (short) 1,
                (byte) 1,
                (byte) 0,
                RankInfo.Normal,
                (byte) 1,
                (byte) 0,
                0,
                TimeInfo.Normal,
                TimeMarker.Empty);
        messageHeat1Lane2 = new DataHandlingMessage(
                "1",
                "2",
                MessageType.ReadyToStart,
                KindOfTime.Empty,
                TimeType.Empty,
                new UsedLanes(getLanes()),
                (byte) 0,
                (short) 1,
                (byte) 1,
                (byte) 0,
                RankInfo.Normal,
                (byte) 2,
                (byte) 0,
                0,
                TimeInfo.Normal,
                TimeMarker.Empty);

        messageHeat2Lane1 = new DataHandlingMessage(
                "1",
                "2",
                MessageType.ReadyToStart,
                KindOfTime.Empty,
                TimeType.Empty,
                new UsedLanes(getLanes()),
                (byte) 0,
                (short) 1,
                (byte) 2,
                (byte) 0,
                RankInfo.Normal,
                (byte) 1,
                (byte) 0,
                0,
                TimeInfo.Normal,
                TimeMarker.Empty);
        messageHeat2Lane2 = new DataHandlingMessage(
                "1",
                "2",
                MessageType.ReadyToStart,
                KindOfTime.Empty,
                TimeType.Empty,
                new UsedLanes(getLanes()),
                (byte) 0,
                (short) 1,
                (byte) 2,
                (byte) 0,
                RankInfo.Normal,
                (byte) 2,
                (byte) 0,
                0,
                TimeInfo.Normal,
                TimeMarker.Empty);
    }

    private boolean[] getLanes() {
        boolean[] bs = new boolean[10];
        for (int x = 0; x < 10; x++) {
            bs[x] = x % 2 == 0;
        }
        return bs;
    }

    @AfterEach
    void cleanup() {
        messages = null;
        messageHeat1Lane1 = null;
        messageHeat1Lane2 = null;
        messageHeat2Lane1 = null;
        messageHeat2Lane2 = null;
    }

    @Test
    void empty() {
        assertEquals(0, messages.size());
    }

    @Test
    void put1Message() {
        messages.put(messageHeat1Lane1, competitionKey);

        assertEquals(1, messages.size());

        assertEquals(1, messages.findBy(competitionKey, messageHeat1Lane1.getEvent(), messageHeat1Lane1.getHeat()).size());
        assertEquals(messageHeat1Lane1String,
                     messages.findBy(competitionKey,
                                     messageHeat1Lane1.getEvent(),
                                     messageHeat1Lane1.getHeat()).get(0).toString());
    }

    @Test
    void put2Messages() {
        messages.put(messageHeat1Lane1, competitionKey);
        messages.put(messageHeat1Lane2, competitionKey);

        assertEquals(2, messages.size());

        assertEquals(2, messages.findBy(competitionKey, messageHeat1Lane1.getEvent(), messageHeat1Lane1.getHeat()).size());
        assertEquals(messageHeat1Lane1String,
                     messages.findBy(competitionKey,
                                     messageHeat1Lane1.getEvent(),
                                     messageHeat1Lane1.getHeat()).get(0).toString());
        assertEquals(messageHeat1Lane2String,
                     messages.findBy(competitionKey,
                                     messageHeat1Lane1.getEvent(),
                                     messageHeat1Lane1.getHeat()).get(1).toString());
    }

    @Test
    void put3Messages() {
        messages.put(messageHeat1Lane1, competitionKey);
        messages.put(messageHeat1Lane2, competitionKey);
        messages.put(messageHeat2Lane1, competitionKey);

        assertEquals(3, messages.size());

        assertEquals(2, messages.findBy(competitionKey, messageHeat1Lane1.getEvent(), messageHeat1Lane1.getHeat()).size());
        assertEquals(messageHeat1Lane1String,
                     messages.findBy(competitionKey,
                                     messageHeat1Lane1.getEvent(),
                                     messageHeat1Lane1.getHeat()).get(0).toString());
        assertEquals(messageHeat1Lane2String,
                     messages.findBy(competitionKey,
                                     messageHeat1Lane1.getEvent(),
                                     messageHeat1Lane1.getHeat()).get(1).toString());

        assertEquals(1, messages.findBy(competitionKey, messageHeat2Lane1.getEvent(), messageHeat2Lane1.getHeat()).size());
        assertEquals(messageHeat2Lane1String,
                     messages.findBy(competitionKey,
                                     messageHeat2Lane1.getEvent(),
                                     messageHeat2Lane1.getHeat()).get(0).toString());
    }

    @Test
    void put4Messages() {
        messages.put(messageHeat1Lane1, competitionKey);
        messages.put(messageHeat1Lane2, competitionKey);
        messages.put(messageHeat2Lane1, competitionKey);
        messages.put(messageHeat2Lane2, competitionKey);

        assertEquals(4, messages.size());

        assertEquals(2, messages.findBy(competitionKey, messageHeat1Lane1.getEvent(), messageHeat1Lane1.getHeat()).size());
        assertEquals(messageHeat1Lane1String,
                     messages.findBy(competitionKey,
                                     messageHeat1Lane1.getEvent(),
                                     messageHeat1Lane1.getHeat()).get(0).toString());
        assertEquals(messageHeat1Lane2String,
                     messages.findBy(competitionKey,
                                     messageHeat1Lane1.getEvent(),
                                     messageHeat1Lane1.getHeat()).get(1).toString());

        assertEquals(2, messages.findBy(competitionKey, messageHeat2Lane1.getEvent(), messageHeat2Lane1.getHeat()).size());
        assertEquals(messageHeat2Lane1String,
                     messages.findBy(competitionKey,
                                     messageHeat2Lane1.getEvent(),
                                     messageHeat2Lane1.getHeat()).get(0).toString());
        assertEquals(messageHeat2Lane2String,
                     messages.findBy(competitionKey,
                                     messageHeat2Lane1.getEvent(),
                                     messageHeat2Lane1.getHeat()).get(1).toString());
    }
}