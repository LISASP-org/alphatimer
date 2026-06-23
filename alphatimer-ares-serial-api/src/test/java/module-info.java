module org.lisasp.alphatimer.test.api.ares.serial {
    requires org.lisasp.alphatimer.api.ares.serial;

    opens org.lisasp.alphatimer.test.api.ares.serial.data;
    opens org.lisasp.alphatimer.test.api.ares.serial.events.dropped;
    opens org.lisasp.alphatimer.test.api.ares.serial.events.messages;
    opens org.lisasp.alphatimer.test.api.ares.serial.events.messages.enums;
    opens org.lisasp.alphatimer.test.api.ares.serial.events.messages.values;

    requires transitive org.junit.jupiter.engine;
    requires transitive org.junit.jupiter.api;
    requires transitive org.junit.jupiter.params;

    requires static lombok;
}
