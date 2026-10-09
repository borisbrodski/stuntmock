package org.eu.stuntmock.unit;

import static org.eu.stuntmock.Stunt.*;
import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.Year;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.eu.stuntmock.StubPartially;
import org.eu.stuntmock.StuntExtension;

/**
 * Classes of the JDK can be declared too, because the dispatcher lives in the bootstrap class loader. This is the
 * basis for freezing time: {@code LocalDate.now()} and friends are ordinary statics.
 */
@ExtendWith(StuntExtension.class)
@StubPartially({LocalDate.class, ZonedDateTime.class})
class JdkClassTest {

    @Test
    void localDateNowCanBeFrozen() {
        LocalDate frozen = LocalDate.of(2000, 6, 15);
        when(() -> LocalDate.now()).thenReturn(frozen);
        assertEquals(frozen, LocalDate.now());
        assertEquals(LocalDate.of(2000, 6, 16), LocalDate.now().plusDays(1)); // the rest of the class is real
    }

    @Test
    void zonedDateTimeNowCanBeFrozen() {
        ZonedDateTime frozen = ZonedDateTime.parse("2030-01-02T03:04:05+01:00[Europe/Berlin]");
        when(() -> ZonedDateTime.now()).thenReturn(frozen);
        assertEquals(2030, ZonedDateTime.now().getYear());
    }

    /** Without a mapping, the JDK class behaves normally. */
    @Test
    void unmappedJdkStaticIsReal() {
        assertEquals(Year.now().getValue(), LocalDate.now().getYear());
    }
}
