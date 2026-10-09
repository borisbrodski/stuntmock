package org.eu.stuntmock.unit;

import static org.eu.stuntmock.Stunt.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.eu.stuntmock.Stub;
import org.eu.stuntmock.StubPartially;
import org.eu.stuntmock.StuntExtension;
import org.eu.stuntmock.UnexpectedCallError;
import org.eu.stuntmock.fakes.Greeter;
import org.eu.stuntmock.fakes.Tools;

/** {@code Stunt.dump()}, the declaration sites in messages, and the debug trace. */
@ExtendWith(StuntExtension.class)
@Stub(Tools.class)
class DiagnosticsTest {

    /** The dump lists declarations with their site, chains with progress, and the calls so far. */
    @Test
    void dumpShowsDeclarationsChainsAndCalls() {
        stubPartially(Greeter.class);
        when(() -> Tools.version()).thenReturn("v");
        verify(Greeter.class, g -> g.greet(arg.anyString())).thenReturn("hi").times(2);
        Tools.version();
        new Greeter().greet("Bob");
        String dump = dump();
        assertTrue(dump.contains("@Stub org.eu.stuntmock.fakes.Tools, declared @Stub on class DiagnosticsTest"),
            dump);
        assertTrue(dump.contains("@StubPartially org.eu.stuntmock.fakes.Greeter, declared at org.eu.stuntmock.unit.DiagnosticsTest."),
            dump);
        assertTrue(dump.contains("when(Tools.version())"), dump);
        assertTrue(dump.contains("thenReturn(\"v\") [1/*]"), dump);
        assertTrue(dump.contains("verify(Greeter.greet(any(String)))"), dump);
        assertTrue(dump.contains("thenReturn(\"hi\") [1/2]"), dump);
        assertTrue(dump.contains("1. Tools.version() -> when(Tools.version()) -> \"v\""), dump);
        assertTrue(dump.matches("(?s).*2\\. Greeter@[0-9a-f]+\\.greet\\(\"Bob\"\\) -> verify.*"), dump);
        new Greeter().greet("Ann");
    }

    /** {@code dump(Class)} restricts the output to one declared class. */
    @Test
    void dumpCanBeFilteredByClass() {
        stubPartially(Greeter.class);
        when(() -> Tools.version()).thenReturn("v");
        String dump = dump(Greeter.class);
        assertTrue(dump.contains("Greeter"), dump);
        assertFalse(dump.contains("when(Tools.version())"), dump);
    }

    /** {@code printMocks()} writes the dump to standard output, for use from production code or a debugger. */
    @Test
    void printMocksWritesToStdout() {
        PrintStream original = System.out;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out));
        try {
            printMocks();
        }
        finally {
            System.setOut(original);
        }
        assertTrue(out.toString().contains("Stunt test scope"), out.toString());
    }

    /** Every failure message carries the dump, so the reader sees what was declared. */
    @Test
    void failureMessagesIncludeTheDump() {
        verify(() -> Tools.version()).never();
        UnexpectedCallError e = assertThrows(UnexpectedCallError.class, Tools::version);
        assertTrue(e.getMessage().contains("Declarations:"), e.getMessage());
        assertTrue(e.getMessage().contains("Calls on declared classes:"), e.getMessage());
        TestSupport.clearRecordedFailures();
    }

    /** With DEBUG enabled for {@code org.eu.stuntmock}, every dispatch decision is logged. */
    @Test
    void debugLogTracesEveryDispatchDecision() {
        Logger logger = Logger.getLogger("org.eu.stuntmock");
        List< String > records = new ArrayList<>();
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                records.add(record.getMessage());
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        Level before = logger.getLevel();
        logger.setLevel(Level.FINE);
        logger.addHandler(handler);
        try {
            when(() -> Tools.doubled(arg.anyInt())).thenReturn(4);
            Tools.doubled(2);
            Tools.tripled(2);
        }
        finally {
            logger.removeHandler(handler);
            logger.setLevel(before);
        }
        assertTrue(records.stream().anyMatch(r -> r.contains("call Tools.doubled(2) -> when(Tools.doubled(any(int))) -> 4")),
            records.toString());
        assertTrue(records.stream().anyMatch(r -> r.contains("call Tools.tripled(2) -> default 0")), records.toString());
    }
}
