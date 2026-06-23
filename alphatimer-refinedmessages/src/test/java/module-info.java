module org.lisasp.alphatimer.test.refinedmessages {
    opens org.lisasp.alphatimer.test.refinedmessages;

    // requires transitive org.lisasp.alphatimer.api.ares.serial;
    // requires org.lisasp.alphatimer.api.refinedmessages;
    requires transitive org.lisasp.alphatimer.refinedmessages;

    requires transitive org.junit.jupiter.engine;
    requires transitive org.junit.jupiter.api;
    requires transitive org.junit.jupiter.params;
}
