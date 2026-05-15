package org.flossware.jremote;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.function.Supplier;

/**
 * Unit tests for InstanceFactory.
 */
class InstanceFactoryTest {

    @Test
    void testReflectionFactoryNoArgs() {
        InstanceFactory<TestService> factory = new InstanceFactory<>(
            TestService.class,
            TestServiceImpl.class
        );

        TestService instance = factory.createInstance();

        assertNotNull(instance);
        assertTrue(instance instanceof TestServiceImpl);
        assertEquals(TestService.class, factory.getInterfaceClass());
    }

    @Test
    void testReflectionFactoryWithArgs() {
        InstanceFactory<ConfigurableService> factory = new InstanceFactory<>(
            ConfigurableService.class,
            ConfigurableServiceImpl.class
        );

        ConfigurableService instance = factory.createInstance(
            new Class<?>[]{String.class, Integer.class},
            new Object[]{"test", 8080}
        );

        assertNotNull(instance);
        assertEquals("test", instance.getConfig());
        assertEquals(8080, instance.getPort());
    }

    @Test
    void testCustomSupplierFactory() {
        InstanceFactory<TestService> factory = new InstanceFactory<>(
            TestService.class,
            () -> new TestServiceImpl()
        );

        TestService instance = factory.createInstance();

        assertNotNull(instance);
        assertTrue(instance instanceof TestServiceImpl);
    }

    @Test
    void testCustomSupplierWithInitialization() {
        InstanceFactory<TestService> factory = new InstanceFactory<>(
            TestService.class,
            () -> {
                TestServiceImpl impl = new TestServiceImpl();
                // Could do custom initialization here
                return impl;
            }
        );

        TestService instance = factory.createInstance();

        assertNotNull(instance);
    }

    @Test
    void testCustomSupplierDoesNotSupportArgs() {
        InstanceFactory<TestService> factory = new InstanceFactory<>(
            TestService.class,
            () -> new TestServiceImpl()
        );

        assertThrows(UnsupportedOperationException.class, () ->
            factory.createInstance(new Class<?>[]{String.class}, new Object[]{"arg"})
        );
    }

    @Test
    void testNullInterfaceRejected() {
        assertThrows(IllegalArgumentException.class, () ->
            new InstanceFactory<>(null, TestServiceImpl.class)
        );
    }

    @Test
    void testNullImplementationRejected() {
        assertThrows(IllegalArgumentException.class, () ->
            new InstanceFactory<>(TestService.class, (Class<TestServiceImpl>) null)
        );
    }

    @Test
    void testNullSupplierRejected() {
        assertThrows(IllegalArgumentException.class, () ->
            new InstanceFactory<>(TestService.class, (Supplier<TestService>) null)
        );
    }

    @Test
    void testNonInterfaceClassRejected() {
        assertThrows(IllegalArgumentException.class, () ->
            new InstanceFactory<>(TestServiceImpl.class, TestServiceImpl.class)
        );
    }

    @Test
    void testImplementationDoesNotImplementInterface() {
        // TestServiceImpl doesn't implement OrderService
        assertThrows(IllegalArgumentException.class, () -> {
            @SuppressWarnings("unchecked")
            Class<? extends OrderService> badClass = (Class<? extends OrderService>) (Class<?>) TestServiceImpl.class;
            new InstanceFactory<>(OrderService.class, badClass);
        });
    }

    @Test
    void testMissingConstructor() {
        InstanceFactory<TestService> factory = new InstanceFactory<>(
            TestService.class,
            TestServiceImpl.class
        );

        // TestServiceImpl doesn't have a constructor that takes a String and Integer
        assertThrows(RuntimeException.class, () ->
            factory.createInstance(
                new Class<?>[]{String.class, Integer.class},
                new Object[]{"test", 123}
            )
        );
    }

    @Test
    void testParameterMismatch() {
        InstanceFactory<ConfigurableService> factory = new InstanceFactory<>(
            ConfigurableService.class,
            ConfigurableServiceImpl.class
        );

        // Wrong number of arguments
        assertThrows(IllegalArgumentException.class, () ->
            factory.createInstance(
                new Class<?>[]{String.class},
                new Object[]{"test", 123}  // 2 args but only 1 type
            )
        );
    }
}
