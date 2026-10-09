package org.eu.stuntmock.unit;

import static org.eu.stuntmock.Stunt.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.eu.stuntmock.Stub;
import org.eu.stuntmock.StubPartially;
import org.eu.stuntmock.Stubbing;
import org.eu.stuntmock.StuntException;
import org.eu.stuntmock.StuntExtension;
import org.eu.stuntmock.fakes.Greeter;
import org.eu.stuntmock.fakes.PriceService;
import org.eu.stuntmock.fakes.Tools;

/** Every misuse Stunt detects while a test declares or maps, one test per message. */
@ExtendWith(StuntExtension.class)
@StubPartially({Tools.class, Greeter.class})
class MisuseTest {

    private static String messageOf(org.junit.jupiter.api.function.Executable executable) {
        return assertThrows(StuntException.class, executable).getMessage();
    }

    @Test
    void whenOutsideAnyScope() throws InterruptedException {
        AtomicReference< Throwable > error = new AtomicReference<>();
        Thread other = new Thread(() -> {
            try {
                when(() -> Tools.version());
            }
            catch (Throwable t) {
                error.set(t);
            }
        });
        other.start();
        other.join();
        assertNotNull(error.get());
        assertTrue(error.get().getMessage().contains("No Stunt scope is open"), error.get().getMessage());
    }

    @Test
    void closureWithoutACall() {
        String m = messageOf(() -> when(() -> {
        }));
        assertTrue(m.contains("does not call any method"), m);
    }

    @Test
    void closureThrowingBeforeTheCall() {
        String m = messageOf(() -> when(() -> Tools.doubled(Integer.parseInt("not a number"))));
        assertTrue(m.contains("closure threw"), m);
        assertTrue(m.contains("before the call Tools.doubled"), m);
    }

    @Test
    void closureCallingAnotherClassThanTheGivenOne() {
        String m = messageOf(() -> when(Greeter.class, g -> Tools.version()));
        assertTrue(m.contains("calls Tools.version instead of a method of Greeter"), m);
    }

    @Test
    void thenReturnWithWrongType() {
        String m = messageOf(() -> when(() -> Tools.doubled(1)).thenReturn("text"));
        assertTrue(m.contains("cannot be returned from a method returning int"), m);
    }

    @Test
    void thenReturnNullForPrimitive() {
        String m = messageOf(() -> when(() -> Tools.doubled(1)).thenReturn((Object) null));
        assertTrue(m.contains("null cannot be returned from a method returning int"), m);
    }

    @Test
    void thenReturnOnVoidMethod() {
        String m = messageOf(() -> when(() -> Tools.reset()).thenReturn("x"));
        assertTrue(m.contains("the method is void"), m);
    }

    @Test
    void thenCallOriginalOnAbstractMethod(@Stub(proxy = true) PriceService prices) {
        String m = messageOf(() -> when(() -> prices.rateFor("DE")).thenCallOriginal());
        assertTrue(m.contains("is abstract"), m);
    }

    @Test
    void negativeCount() {
        String m = messageOf(() -> verify(() -> Tools.version()).times(-1));
        assertTrue(m.contains("must not be negative"), m);
        Tools.version();
    }

    @Test
    void thenReturnsWithLazyIterableInVerify() {
        Iterable< String > lazy = () -> List.of("a").iterator();
        String m = messageOf(() -> verify(() -> Tools.version()).thenReturns(lazy));
        assertTrue(m.contains("needs a Collection"), m);
        Tools.version();
    }

    @Test
    void thenReturnsCombinedWithADifferentCount() {
        String m = messageOf(() -> when(() -> Tools.version()).thenReturns(List.of("a", "b")).times(3));
        assertTrue(m.contains("2 values cannot be combined with times(3)"), m);
    }

    @Test
    void chainChangedAfterFirstUse() {
        Stubbing chain = when(() -> Tools.version()).thenReturn("a");
        Tools.version();
        String m = messageOf(() -> chain.thenReturn("b"));
        assertTrue(m.contains("cannot be changed after the test started using it"), m);
    }

    @Test
    void matchersWithAMethodReference() {
        String m = messageOf(() -> {
            arg.anyInt();
            when(Tools::version);
        });
        assertTrue(m.contains("matchers cannot be combined with a method reference"), m);
    }

    @Test
    void closureWhoseCallIsNotIntercepted() {
        // the receiver is a subclass instance whose overriding method body is not instrumented
        Greeter anonymous = new Greeter() {
            @Override
            public String greet(String name) {
                return "anon";
            }
        };
        String m = messageOf(() -> when(() -> anonymous.greet("x")));
        assertTrue(m.contains("was not intercepted"), m);
    }
}
