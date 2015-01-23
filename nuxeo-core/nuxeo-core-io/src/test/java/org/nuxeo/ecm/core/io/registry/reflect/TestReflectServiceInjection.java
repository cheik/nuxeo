package org.nuxeo.ecm.core.io.registry.reflect;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.nuxeo.ecm.core.io.registry.reflect.Instanciations.EACH_TIME;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.io.registry.context.RenderingContext;
import org.nuxeo.ecm.core.schema.SchemaManager;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

import com.google.inject.Inject;

@RunWith(FeaturesRunner.class)
@Features(CoreFeature.class)
public class TestReflectServiceInjection {

    private final RenderingContext ctx = RenderingContext.Builder.get();

    @Test
    public void noInjectionIfNoAnnotation() throws Exception {
        MarshallerInspector inspector = new MarshallerInspector(NoInjectionMarshaller.class);
        NoInjectionMarshaller instance = inspector.getInstance(ctx);
        assertNull(instance.service);
    }

    @Test
    public void injectService() throws Exception {
        MarshallerInspector inspector = new MarshallerInspector(SimpleServiceMarshaller.class);
        SimpleServiceMarshaller instance = inspector.getInstance(ctx);
        assertNotNull(instance.service);
    }

    @Test
    public void inheritInjection() throws Exception {
        MarshallerInspector inspector = new MarshallerInspector(InheritMarshaller.class);
        InheritMarshaller instance = inspector.getInstance(ctx);
        assertNotNull(instance.service);
        assertNotNull(instance.service2);
    }

    @Setup(mode = EACH_TIME)
    public static class SimpleServiceMarshaller {
        @Inject
        protected SchemaManager service;
    }

    @Setup(mode = EACH_TIME)
    public static class NoInjectionMarshaller {
        private SchemaManager service;
    }

    @Setup(mode = EACH_TIME)
    public static class InheritMarshaller extends SimpleServiceMarshaller {
        @Inject
        private SchemaManager service2;
    }

}
