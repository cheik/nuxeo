package org.nuxeo.ecm.core.io.registry.reflect;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.nuxeo.ecm.core.io.registry.context.RenderingContext;
import org.nuxeo.ecm.core.io.registry.context.ThreadSafeRenderingContext;

import com.google.inject.Inject;

public class TestReflectContextInjection {

    private final RenderingContext ctx = RenderingContext.Builder.get();

    @Test
    public void noInjectionIfNoAnnotation() throws Exception {
        MarshallerInspector inspector = new MarshallerInspector(NoInjectionMarshaller.class);
        NoInjectionMarshaller instance = inspector.getInstance(ctx);
        assertNull(instance.ctx);
    }

    @Test
    public void ifNullContextInjectEmptyContext() throws Exception {
        MarshallerInspector inspector = new MarshallerInspector(SingletonMarshaller.class);
        SingletonMarshaller instance = inspector.getInstance(null);
        assertNotNull(instance.ctx);
        assertTrue(instance.ctx.getAllParameters().isEmpty());
    }

    @Test
    public void ifThreadSafeContextInjectDelegateContext() throws Exception {
        ThreadSafeRenderingContext tsCtx = new ThreadSafeRenderingContext();
        tsCtx.configureThread(ctx);
        MarshallerInspector inspector = new MarshallerInspector(EachTimeMarshaller.class);
        EachTimeMarshaller instance = inspector.getInstance(tsCtx);
        assertNotNull(instance.ctx);
        assertSame(ctx, instance.ctx);
    }

    @Test
    public void injectInEachTimeInstance() throws Exception {
        MarshallerInspector inspector = new MarshallerInspector(EachTimeMarshaller.class);
        EachTimeMarshaller instance = inspector.getInstance(ctx);
        assertSame(ctx, instance.ctx);
    }

    @Test
    public void injectInPerThreadInstance() throws Exception {
        final MarshallerInspector inspector = new MarshallerInspector(PerThreadMarshaller.class);
        PerThreadMarshaller instance1 = inspector.getInstance(ctx);
        assertSame(ctx, instance1.ctx);
        Thread subThread = new Thread() {
            @Override
            public void run() {
                synchronized (this) {
                    // in a different thread, it should be a different instance but same context
                    final PerThreadMarshaller instance2 = inspector.getInstance(ctx);
                    assertSame(ctx, instance2.ctx);
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
    public void replaceContextInPerThreadInstance() throws Exception {
        final MarshallerInspector inspector = new MarshallerInspector(PerThreadMarshaller.class);
        PerThreadMarshaller instance1 = inspector.getInstance(ctx);
        RenderingContext ctx2 = RenderingContext.Builder.get();
        PerThreadMarshaller instance2 = inspector.getInstance(ctx2);
        assertSame(ctx2, instance1.ctx);
        assertSame(ctx2, instance2.ctx);
    }

    @Test
    public void injectInSingletonInstance() throws Exception {
        final MarshallerInspector inspector = new MarshallerInspector(SingletonMarshaller.class);
        SingletonMarshaller instance1 = inspector.getInstance(ctx);
        assertNotSame(ctx, instance1.ctx);
        assertTrue(instance1.ctx instanceof ThreadSafeRenderingContext);
        ThreadSafeRenderingContext safeCtx = (ThreadSafeRenderingContext) instance1.ctx;
        assertNotNull(safeCtx.getDelegate());
        assertSame(ctx, safeCtx.getDelegate());
        Thread subThread = new Thread() {
            @Override
            public void run() {
                synchronized (this) {
                    // in a different thread, it should be a different instance but same context
                    final SingletonMarshaller instance2 = inspector.getInstance(ctx);
                    assertNotSame(ctx, instance2.ctx);
                    assertTrue(instance2.ctx instanceof ThreadSafeRenderingContext);
                    ThreadSafeRenderingContext safeCtx = (ThreadSafeRenderingContext) instance2.ctx;
                    assertNotNull(safeCtx.getDelegate());
                    assertSame(ctx, safeCtx.getDelegate());
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
    public void replaceContextInSingletonInstance() throws Exception {
        final MarshallerInspector inspector = new MarshallerInspector(SingletonMarshaller.class);
        SingletonMarshaller instance1 = inspector.getInstance(ctx);
        RenderingContext ctx2 = RenderingContext.Builder.get();
        SingletonMarshaller instance2 = inspector.getInstance(ctx2);
        ThreadSafeRenderingContext safeCtx1 = (ThreadSafeRenderingContext) instance1.ctx;
        ThreadSafeRenderingContext safeCtx2 = (ThreadSafeRenderingContext) instance2.ctx;
        assertSame(ctx2, safeCtx1.getDelegate());
        assertSame(ctx2, safeCtx2.getDelegate());
    }

    @Test
    public void inheritInjection() throws Exception {
        MarshallerInspector inspector = new MarshallerInspector(InheritMarshaller.class);
        InheritMarshaller instance = inspector.getInstance(ctx);
        assertSame(ctx, instance.ctx);
        assertSame(ctx, instance.ctx2);
    }

    @Setup(mode = Instanciations.EACH_TIME)
    public static class NoInjectionMarshaller {
        private RenderingContext ctx;
    }

    @Setup(mode = Instanciations.EACH_TIME)
    public static class EachTimeMarshaller {
        @Inject
        protected RenderingContext ctx;
    }

    @Setup(mode = Instanciations.PER_THREAD)
    public static class PerThreadMarshaller {
        @Inject
        private RenderingContext ctx;
    }

    @Setup(mode = Instanciations.SINGLETON)
    public static class SingletonMarshaller {
        @Inject
        private RenderingContext ctx;
    }

    @Setup(mode = Instanciations.EACH_TIME)
    public static class InheritMarshaller extends EachTimeMarshaller {
        @Inject
        private RenderingContext ctx2;
    }

}
