package org.eu.de.stuntmock.unit;

import static org.eu.de.stuntmock.Stunt.*;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.eu.de.stuntmock.MissingCallError;
import org.eu.de.stuntmock.Stub;
import org.eu.de.stuntmock.StubPartially;
import org.eu.de.stuntmock.StuntException;
import org.eu.de.stuntmock.StuntExtension;
import org.eu.de.stuntmock.StuntSettings;
import org.eu.de.stuntmock.TypeResolver;
import org.eu.de.stuntmock.fakes.Entity;
import org.eu.de.stuntmock.fakes.Locator;
import org.eu.de.stuntmock.fakes.PriceService;
import org.eu.de.stuntmock.fakes.Repository;
import org.eu.de.stuntmock.fakes.RepositoryImpl;
import org.eu.de.stuntmock.fakes.Service;
import org.eu.de.stuntmock.fakes.TaxCalculator;
import org.eu.de.stuntmock.internal.Scope;

/**
 * Located types: an interface declared in the test is resolved by a {@link org.eu.de.stuntmock.TypeResolver}
 * to the implementation the production code obtains from its locator. The handle is that very instance. Without a
 * resolver, an interface gives an instance-scoped proxy that the test must hand over.
 */
@ExtendWith(StuntExtension.class)
class ResolverTest {

    static {
        // what a shared base test class does once in its static initializer
        StuntSettings.addTypeResolver(new Locator.Resolver());
    }

    /** The handle is the located instance; the mapping applies to whatever the production code looks up. */
    @Test
    void interfaceDeclarationResolvesToTheLocatedImplementation(@StubPartially Repository repository) {
        assertSame(Locator.get(Repository.class), repository);
        when(() -> repository.load(1L)).thenReturn("stubbed-1");
        Service service = new Service();
        assertEquals("stubbed-1", service.load(1L));
        assertEquals("real-2", service.load(2L));
    }

    /** The entity reaches the repository through the locator; the test never touches either. */
    @Test
    void entityRemoveReachesTheRepository(@StubPartially Repository repository) {
        Entity e1 = new Entity(1);
        Entity e2 = new Entity(2);
        verify(() -> repository.remove(e1)).thenDoNothing();
        verify(() -> repository.remove(e2)).thenDoNothing();
        new Service().removeAll(e1, e2);
        assertTrue(((RepositoryImpl) repository).removed.isEmpty());
    }

    /** Declaring the implementation class directly lands on the same declaration and handle. */
    @Test
    void implementationAndInterfaceAreOneDeclaration() {
        Repository byInterface = stub(Repository.class);
        RepositoryImpl byImpl = stub(RepositoryImpl.class);
        assertSame(byInterface, byImpl);
        assertNull(new Service().load(5L));
    }

    /** An interface without a resolver can be an instance mock, explicitly: the test passes the handle to the code under test. */
    @Test
    void unresolvedInterfaceIsAnInstanceMockOnRequest(@Stub(proxy = true) PriceService prices) {
        when(() -> prices.rateFor("DE")).thenReturn(0.19);
        assertEquals(19.0, new TaxCalculator(prices).taxFor("DE", 100), 0.0001);
        assertEquals(0.0, prices.rateFor("FR"), 0.0001);   // unmapped on a @Stub proxy: default
        assertNull(prices.currency());                      // default methods are mocked too
        assertTrue(prices.toString().startsWith("handle of PriceService@"), prices.toString());
    }

    /** ... and the message for a missed call explains the difference. */
    @Test
    void missedCallOnInstanceMockIsExplained(@Stub(proxy = true) PriceService prices) {
        verify(() -> prices.rateFor("DE")).thenReturn(0.19);
        MissingCallError e = assertThrows(MissingCallError.class, () -> Scope.testScope().verifyAll());
        assertTrue(e.getMessage().contains("instance mock, calls on this object only"), e.getMessage());
        prices.rateFor("DE");
    }

    /** Without the request, an unresolved interface is an error that names the ways out, ordering included. */
    @Test
    void unresolvedInterfaceWithoutProxyRequestIsRejected() {
        StuntException e = assertThrows(StuntException.class, () -> stub(PriceService.class));
        assertTrue(e.getMessage().contains("no TypeResolver provided its instance"), e.getMessage());
        assertTrue(e.getMessage().contains("static field is bound before any @BeforeAll"), e.getMessage());
        assertTrue(e.getMessage().contains("stubProxy(PriceService.class)"), e.getMessage());
    }

    @Test
    void stubProxyIsTheProgrammaticForm() {
        PriceService prices = verify(stubProxy(PriceService.class));
        when(() -> prices.rateFor("DE")).thenReturn(0.5);
        assertEquals(0.5, prices.rateFor("DE"));
        assertTrue(dump().contains("@Stub(proxy) @Verify " + PriceService.class.getName()), dump());
    }

    @Test
    void proxyRequestOnAClassIsRejected() {
        StuntException e = assertThrows(StuntException.class, () -> stubProxy(Service.class));
        assertTrue(e.getMessage().contains("is a class and is intercepted in place"), e.getMessage());
    }

    @Test
    void proxyRequestOnAResolvedInterfaceIsRejected() {
        StuntException e = assertThrows(StuntException.class, () -> stubProxy(Repository.class));
        assertTrue(e.getMessage().contains("proxy is only for types no resolver knows"), e.getMessage());
    }

    /** A partial stub of an unresolvable interface has no original code to run. */
    @Test
    void partialStubOfUnresolvedInterfaceIsRejected() {
        StuntException e = assertThrows(StuntException.class, () -> stubPartially(PriceService.class));
        assertTrue(e.getMessage().contains("@StubPartially is not possible"), e.getMessage());
    }

    // ---------------------------------------------------------------- resolver contract

    static final class ThrowingResolver implements TypeResolver {
        final boolean inImplementation;

        ThrowingResolver(boolean inImplementation) {
            this.inImplementation = inImplementation;
        }

        @Override
        public Object instanceOf(Class< ? > type) {
            if (type != Service.class) {
                return null;
            }
            if (inImplementation) {
                return new Service();
            }
            throw new IllegalStateException("lookup failed");
        }

        @Override
        public Class< ? > implementationOf(Class< ? > type, Object instance) {
            throw new IllegalStateException("no such class");
        }
    }

    /** An exception in {@code instanceOf} is never swallowed: it fails with the resolver, the type and the cause. */
    @Test
    void exceptionInInstanceOfFailsWithCause() {
        withResolver(new ThrowingResolver(false), () -> {
            StuntException e = assertThrows(StuntException.class, () -> stub(Service.class));
            assertTrue(e.getMessage().contains("ThrowingResolver threw while resolving"), e.getMessage());
            assertTrue(e.getMessage().contains("Service (instanceOf)"), e.getMessage());
            assertEquals("lookup failed", e.getCause().getMessage());
        });
    }

    /** The same for {@code implementationOf}. */
    @Test
    void exceptionInImplementationOfFailsWithCause() {
        withResolver(new ThrowingResolver(true), () -> {
            StuntException e = assertThrows(StuntException.class, () -> stub(Service.class));
            assertTrue(e.getMessage().contains("Service (implementationOf)"), e.getMessage());
            assertEquals("no such class", e.getCause().getMessage());
        });
    }

    /**
     * Resolvers are asked for concrete classes too, so that a class declared by its implementation gets the
     * located object as handle, the same one the production code sees.
     */
    @Test
    void resolverIsAskedForClassesToo() {
        RepositoryImpl handle = stubPartially(RepositoryImpl.class);
        assertSame(Locator.get(Repository.class), handle);
    }

    /** JDK types are never offered to resolvers. */
    @Test
    void resolverIsNotAskedForJdkTypes() {
        java.util.List< Class< ? > > asked = new java.util.ArrayList<>();
        withResolver(type -> {
            asked.add(type);
            return null;
        }, () -> {
            stubPartially(java.time.LocalDate.class);
            stubPartially(Service.class);
        });
        assertEquals(java.util.List.of(Service.class), asked);
    }

    /** A proxied instance: the override names the class that holds the code, so fresh instances count as well. */
    @Test
    void implementationOfOverrideForProxiedInstances() {
        Service proxied = new Service() { };          // an anonymous subclass stands in for a Hibernate proxy
        withResolver(new TypeResolver() {
            @Override
            public Object instanceOf(Class< ? > type) {
                return type == Service.class ? proxied : null;
            }

            @Override
            public Class< ? > implementationOf(Class< ? > type, Object instance) {
                return Service.class;
            }
        }, () -> {
            Service handle = stubPartially(Service.class);
            assertSame(proxied, handle);
            when(() -> handle.load(arg.anyLong())).thenReturn("stubbed");
            assertEquals("stubbed", new Service().load(1L));   // a fresh instance of the real class
        });
    }

    /** The default {@code implementationOf} is the instance's class; a mismatch is reported. */
    @Test
    void implementationMustMatchTheInstance() {
        withResolver(new TypeResolver() {
            @Override
            public Object instanceOf(Class< ? > type) {
                return type == Service.class ? new Service() : null;
            }

            @Override
            public Class< ? > implementationOf(Class< ? > type, Object instance) {
                return RepositoryImpl.class;
            }
        }, () -> {
            StuntException e = assertThrows(StuntException.class, () -> stub(Service.class));
            assertTrue(e.getMessage().contains("but its instance is a"), e.getMessage());
        });
    }

    private static void withResolver(TypeResolver resolver, Runnable block) {
        StuntSettings.addTypeResolver(resolver);
        try {
            block.run();
        }
        finally {
            StuntSettings.removeTypeResolver(resolver);
        }
    }
}
