package org.lisasp.alphatiming.cli.analysis;

import org.lisasp.alphatimer.api.ares.serial.Characters;
import org.lisasp.alphatimer.api.ares.serial.events.DataInputEvent;
import org.lisasp.alphatimer.api.ares.serial.events.dropped.DataHandlingMessage1DroppedEvent;
import org.lisasp.alphatimer.api.ares.serial.events.dropped.DataHandlingMessage2DroppedEvent;
import org.lisasp.alphatimer.api.ares.serial.events.dropped.UnknownMessageDroppedEvent;
import org.lisasp.alphatimer.api.ares.serial.events.dropped.UnstructuredInputDroppedEvent;
import org.lisasp.alphatimer.api.ares.serial.events.messages.DataHandlingMessage;
import org.lisasp.alphatimer.api.ares.serial.events.messages.DataHandlingMessage1;
import org.lisasp.alphatimer.api.ares.serial.events.messages.DataHandlingMessage2;
import org.lisasp.alphatimer.api.ares.serial.events.messages.Ping;
import org.lisasp.alphatimer.api.ares.serial.events.messages.ScoreboardMessage;
import org.lisasp.alphatimer.api.refinedmessages.RefinedMessage;
import org.lisasp.alphatimer.api.refinedmessages.dropped.DroppedRefinedMessage;
import org.lisasp.alphatimer.ares.serial.InputCollector;
import org.lisasp.alphatimer.ares.serial.MessageAggregator;
import org.lisasp.alphatimer.ares.serial.MessageConverter;
import org.lisasp.alphatimer.refinedmessages.DataHandlingMessageRefiner;
import org.lisasp.basics.jre.date.ActualDateTime;

import java.io.IOException;
import java.io.InputStream;

/**
 * Runs recorded bytes through the same pipeline as a recording (collector, parser, aggregator, refiner) and sorts what
 * it cannot use into defects (the protocol layer rejects it) and messages that are well-formed but not implemented
 * (the refiner drops them).
 */
public final class CaptureAnalysis {

    private static final String PING_BYTES = "\u0012\u00149TP";

    private final CaptureStatistics statistics;

    public CaptureAnalysis(CaptureStatistics statistics) {
        this.statistics = statistics;
    }

    public void analyse(String file, InputStream in) throws IOException {
        new FileRun(file).run(in);
    }

    private final class FileRun {

        private final String file;
        private final InputCollector collector = new InputCollector("", new ActualDateTime());
        private final MessageAggregator aggregator = new MessageAggregator();
        private final DataHandlingMessageRefiner refiner = new DataHandlingMessageRefiner();
        private DataHandlingMessage1 pending;
        private DataHandlingMessage current;
        private boolean refined;
        private boolean dropped;

        FileRun(String file) {
            this.file = file;
            MessageConverter converter = new MessageConverter();
            converter.register(this::protocolEvent);
            aggregator.register(this::dataHandlingMessage);
            refiner.register(this::refinedMessage);
            collector.register(converter);
        }

        void run(InputStream in) throws IOException {
            long bytes = 0;
            byte[] buffer = new byte[8192];
            for (int read = in.read(buffer); read >= 0; read = in.read(buffer)) {
                for (int i = 0; i < read; i++) {
                    collector.accept(buffer[i]);
                }
                bytes += read;
            }
            collector.close();
            if (pending != null) {
                finding(FindingKind.MESSAGE_1_WITHOUT_MESSAGE_2, "", PrintableBytes.of(pending.getOriginal()));
            }
            statistics.file(file, bytes);
        }

        private void protocolEvent(DataInputEvent event) {
            switch (event) {
                case DataHandlingMessage1 m -> {
                    endPendingWithoutMessage2();
                    pending = m;
                }
                case DataHandlingMessage2 m -> {
                    if (pending == null) {
                        finding(FindingKind.MESSAGE_2_WITHOUT_MESSAGE_1, "", PrintableBytes.of(m.getOriginal()));
                    }
                    pending = null;
                }
                case Ping p -> statistics.protocolMessage("Ping");
                case ScoreboardMessage s -> {
                    String type = "Scoreboard " + s.getCommand().name();
                    statistics.protocolMessage(type);
                    statistics.processedMessage(type, false);
                }
                case DataHandlingMessage2DroppedEvent d -> {
                    pending = null;
                    finding(FindingKind.INVALID_MESSAGE, invalid(d, d.getMessage()), PrintableBytes.of(d.getData().getDataCopy()));
                }
                case DataHandlingMessage1DroppedEvent d -> {
                    endPendingWithoutMessage2();
                    finding(FindingKind.INVALID_MESSAGE, invalid(d, d.getMessage()), PrintableBytes.of(d.getData().getDataCopy()));
                }
                case UnstructuredInputDroppedEvent d -> {
                    endPendingWithoutMessage2();
                    finding(FindingKind.UNFRAMED_DATA, "", PrintableBytes.of(d.getData().getDataCopy()));
                }
                case UnknownMessageDroppedEvent d -> {
                    endPendingWithoutMessage2();
                    byte[] data = d.getData().getDataCopy();
                    finding(looksDamaged(data) ? FindingKind.DAMAGED_MESSAGE : FindingKind.UNKNOWN_MESSAGE, "", PrintableBytes.of(data));
                }
                default -> endPendingWithoutMessage2();
            }
            aggregator.accept(event);
        }

        private void endPendingWithoutMessage2() {
            if (pending != null) {
                finding(FindingKind.MESSAGE_1_WITHOUT_MESSAGE_2, "", PrintableBytes.of(pending.getOriginal()));
                pending = null;
            }
        }

        private void dataHandlingMessage(DataHandlingMessage message) {
            current = message;
            refined = false;
            dropped = false;
            statistics.protocolMessage(message.getMessageType().name());
            refiner.accept(message);
            statistics.processedMessage(message.getMessageType().name(), refined && !dropped);
        }

        private void refinedMessage(RefinedMessage message) {
            if (message instanceof DroppedRefinedMessage) {
                dropped = true;
                finding(FindingKind.NOT_REFINED, signature(message, current),
                        PrintableBytes.of(current.getOriginalText1()) + " " + PrintableBytes.of(current.getOriginalText2()));
            } else {
                refined = true;
                statistics.refinedMessage(message.getClass().getSimpleName());
            }
        }

        private void finding(FindingKind kind, String detail, String example) {
            statistics.finding(kind, detail, file, example);
        }
    }

    private static String invalid(DataInputEvent event, String reason) {
        return event.getClass().getSimpleName() + ": " + reason;
    }

    /**
     * An unknown message is damaged rather than new if it begins like a data handling message or a ping, or consists only
     * of the bytes of a ping (a ping with bytes lost or repeated).
     */
    private static boolean looksDamaged(byte[] data) {
        return data[1] == Characters.STX_StartOfText || data[1] == Characters.DC2_Periphery || onlyPingBytes(data);
    }

    private static boolean onlyPingBytes(byte[] data) {
        for (int i = 1; i < data.length - 1; i++) {
            if (PING_BYTES.indexOf(data[i]) < 0) {
                return false;
            }
        }
        return true;
    }

    private static String signature(RefinedMessage dropped, DataHandlingMessage m) {
        return "%s (messageType=%s, kindOfTime=%s, timeType=%s, timeInfo=%s, rankInfo=%s, timeMarker=%s)".formatted(
                dropped.getClass().getSimpleName(), m.getMessageType(), m.getKindOfTime(), m.getTimeType(), m.getTimeInfo(),
                m.getRankInfo(), m.getTimeMarker());
    }
}
