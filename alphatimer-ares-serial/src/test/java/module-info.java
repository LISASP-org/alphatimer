module org.lisasp.alphatimer.test.ares.serial {
    requires org.lisasp.alphatimer.ares.serial;
    requires org.lisasp.alphatimer.api.ares.serial;

    opens org.lisasp.alphatimer.test.ares.serial;
    opens org.lisasp.alphatimer.test.ares.serial.utils;

    requires transitive org.junit.jupiter.engine;
    requires transitive org.junit.jupiter.api;
    requires transitive org.junit.jupiter.params;

    requires static lombok;
}
