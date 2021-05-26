package org.lisasp.alphatimer.server.testdoubles;

import org.lisasp.alphatimer.storage.DateFacade;

import java.time.LocalDate;
import java.time.Month;

public class TestDateFacade implements DateFacade {
    @Override
    public LocalDate today() {
        return LocalDate.of(2021, Month.APRIL, 17);
    }
}
