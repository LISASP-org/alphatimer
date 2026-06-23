module org.lisasp.alphatimer.test.datatests {
    opens org.lisasp.alphatimer.test.datatests;

    requires org.lisasp.alphatimer.datatests;
    requires transitive org.lisasp.alphatimer.api.refinedmessages;
    requires transitive org.lisasp.alphatimer.legacy;

    requires org.lisasp.basics.jre;
    requires org.lisasp.alphatimer.ares.serial;
    requires org.lisasp.alphatimer.refinedmessages;
    requires org.lisasp.alphatimer.test.legacy;

    requires java.base;

    requires xstream;
    requires static lombok;

    requires org.slf4j;

    requires transitive org.junit.jupiter.engine;
    requires transitive org.junit.jupiter.api;
    requires transitive org.junit.jupiter.params;
}
