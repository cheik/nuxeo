package org.nuxeo.ecm.core.io.registry.reflect;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON_TYPE;
import static javax.ws.rs.core.MediaType.APPLICATION_XML;
import static javax.ws.rs.core.MediaType.APPLICATION_XML_TYPE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.nuxeo.ecm.core.io.registry.reflect.Instanciations.EACH_TIME;
import static org.nuxeo.ecm.core.io.registry.reflect.Instanciations.PER_THREAD;
import static org.nuxeo.ecm.core.io.registry.reflect.Instanciations.SINGLETON;

import org.junit.Test;
import org.nuxeo.ecm.core.io.registry.context.RenderingContext;

public class TestReflectInstanciation {

    private final RenderingContext ctx = RenderingContext.Builder.get();

    @Test
    public void canInstanciateWithSetupAndPublicConstructor() throws Exception {
        MarshallerInspector inspector = new MarshallerInspector(SimpleMarshaller.class);
        assertTrue(inspector.isValid());
        Object instance = inspector.getInstance(ctx);
        assertNotNull(instance);
        assertEquals(SimpleMarshaller.class, instance.getClass());
    }

    @Test
    public void cannotInstanciateWithoutSetup() throws Exception {
        MarshallerInspector inspector = new MarshallerInspector(MarshallerWithoutSetup.class);
        assertFalse(inspector.isValid());
        assertNull(inspector.getInstance(ctx));
    }

    @Test
    public void cannotInstanciateWithoutPublicConstructor() throws Exception {
        MarshallerInspector inspector = new MarshallerInspector(MarshallerWithoutPublicConstructor.class);
        assertFalse(inspector.isValid());
        assertNull(inspector.getInstance(ctx));
    }

    @Test
    public void cannotInheritSetup() throws Exception {
        MarshallerInspector inspector = new MarshallerInspector(InheritedSimpleMarshaller.class);
        assertFalse(inspector.isValid());
    }

    @Test
    public void defaultSupportsNothing() throws Exception {
        MarshallerInspector inspector = new MarshallerInspector(SimpleMarshaller.class);
        assertTrue(inspector.getSupports().isEmpty());
    }

    @Test
    public void loadSupports() throws Exception {
        MarshallerInspector inspector = new MarshallerInspector(SupportsMarshaller.class);
        assertEquals(2, inspector.getSupports().size());
        assertTrue(inspector.getSupports().contains(APPLICATION_JSON_TYPE));
        assertTrue(inspector.getSupports().contains(APPLICATION_XML_TYPE));
    }

    @Test
    public void inheritSupports() throws Exception {
        MarshallerInspector inspector = new MarshallerInspector(InheritedSupportsMarshaller.class);
        assertEquals(2, inspector.getSupports().size());
        assertTrue(inspector.getSupports().contains(APPLICATION_JSON_TYPE));
        assertTrue(inspector.getSupports().contains(APPLICATION_XML_TYPE));
    }

    @Test
    public void overrideSupports() throws Exception {
        MarshallerInspector inspector = new MarshallerInspector(OverrideSupportsMarshaller.class);
        assertEquals(1, inspector.getSupports().size());
        assertTrue(inspector.getSupports().contains(APPLICATION_JSON_TYPE));
    }

    @Test
    public void eachTimeInstance() throws Exception {
        MarshallerInspector inspector = new MarshallerInspector(EachTimeMarshaller.class);
        EachTimeMarshaller instance1 = inspector.getInstance(ctx);
        assertNotNull(instance1);
        EachTimeMarshaller instance2 = inspector.getInstance(ctx);
        assertNotNull(instance2);
        assertNotSame(instance1, instance2);
    }

    @Test
    public void perThreadInstance() throws Exception {
        final MarshallerInspector inspector = new MarshallerInspector(PerThreadMarshaller.class);
        PerThreadMarshaller instance1 = inspector.getInstance(ctx);
        assertNotNull(instance1);
        final PerThreadMarshaller instance2 = inspector.getInstance(ctx);
        assertNotNull(instance2);
        assertSame(instance1, instance2);
        Thread subThread = new Thread() {
            @Override
            public void run() {
                synchronized (this) {
                    // in a different thread, it should be a different instance
                    final PerThreadMarshaller instance3 = inspector.getInstance(ctx);
                    assertNotNull(instance3);
                    assertNotSame(instance2, instance3);
                    notify();
                }
            }

        };
        subThread.start();
        synchronized (subThread) {
            subThread.wait();
        }
    }

    @Test
    public void singletonInstance() throws Exception {
        final MarshallerInspector inspector = new MarshallerInspector(SingletonMarshaller.class);
        SingletonMarshaller instance1 = inspector.getInstance(ctx);
        assertNotNull(instance1);
        final SingletonMarshaller instance2 = inspector.getInstance(ctx);
        assertNotNull(instance2);
        assertSame(instance1, instance2);
        Thread subThread = new Thread() {
            @Override
            public void run() {
                synchronized (this) {
                    final SingletonMarshaller instance3 = inspector.getInstance(ctx);
                    assertNotNull(instance3);
                    assertSame(instance2, instance3);
                    notify();
                }
            }

        };
        subThread.start();
        synchronized (subThread) {
            subThread.wait();
        }
    }

    @Setup(mode = SINGLETON)
    public static class MarshallerWithoutPublicConstructor {
        private MarshallerWithoutPublicConstructor() {
        }
    }

    public static class MarshallerWithoutSetup {
    }

    @Setup(mode = SINGLETON)
    public static class SimpleMarshaller {
    }

    public static class InheritedSimpleMarshaller extends SimpleMarshaller {
    }

    @Setup(mode = SINGLETON)
    @Supports({ APPLICATION_JSON, APPLICATION_XML })
    public static class SupportsMarshaller {
    }

    @Setup(mode = SINGLETON)
    public static class InheritedSupportsMarshaller extends SupportsMarshaller {
    }

    @Setup(mode = SINGLETON)
    @Supports(APPLICATION_JSON)
    public static class OverrideSupportsMarshaller extends SupportsMarshaller {
    }

    @Setup(mode = EACH_TIME)
    public static class EachTimeMarshaller {
    }

    @Setup(mode = PER_THREAD)
    public static class PerThreadMarshaller {
    }

    @Setup(mode = SINGLETON)
    public static class SingletonMarshaller {
    }

}
