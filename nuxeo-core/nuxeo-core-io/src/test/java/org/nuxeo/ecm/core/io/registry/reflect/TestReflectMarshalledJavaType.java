package org.nuxeo.ecm.core.io.registry.reflect;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.nuxeo.ecm.core.io.registry.reflect.Instanciations.SINGLETON;
import static org.nuxeo.ecm.core.io.registry.reflect.Priorities.REFERENCE;

import java.io.OutputStream;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.MediaType;

import org.junit.Test;
import org.nuxeo.ecm.core.io.registry.Writer;

public class TestReflectMarshalledJavaType {

    @Test
    public void canGetSupportedJavaType() throws Exception {
        MarshallerInspector inspector = new MarshallerInspector(IntegerWriter.class);
        Class<?> type = inspector.getMarshalledType();
        assertNotNull(type);
        assertEquals(Integer.class, type);
    }

    @Test
    public void handleInheritedJavaType() throws Exception {
        MarshallerInspector inspector = new MarshallerInspector(InheritWriter.class);
        Class<?> type = inspector.getMarshalledType();
        assertNotNull(type);
        assertEquals(Integer.class, type);
    }

    // used for reflect in following test
    @SuppressWarnings("unused")
    private Map<String, List<Integer>> listIntegerMapProperty = null;

    // used for reflect in following test
    @SuppressWarnings("unused")
    private Map<?, ?> mapProperty = null;

    @Test
    public void canGetStereotypedMarshaller() throws Exception {
        Type listIntegerMap = TestReflectMarshalledJavaType.class.getDeclaredField("listIntegerMapProperty").getGenericType();
        Type map = TestReflectMarshalledJavaType.class.getDeclaredField("mapProperty").getGenericType();
        MarshallerInspector inspector = new MarshallerInspector(ListIntegerMapWriter.class);
        assertNotNull(inspector.getGenericType());
        assertEquals(listIntegerMap, inspector.getGenericType());
        assertNotEquals(map, inspector.getGenericType());
    }

    @Setup(mode = SINGLETON)
    public static class IntegerWriter implements Writer<Integer> {
        @Override
        public boolean accept(Class<?> clazz, Type genericType, MediaType mediatype) {
            return true;
        }

        @Override
        public void write(Integer entity, Class<?> clazz, Type genericType, MediaType mediatype, OutputStream out) {
        }
    }

    @Setup(mode = SINGLETON, priority = REFERENCE)
    public static class InheritWriter extends IntegerWriter {
    }

    @Setup(mode = SINGLETON)
    public static class ListIntegerMapWriter implements Writer<Map<String, List<Integer>>> {

        @Override
        public boolean accept(Class<?> clazz, Type genericType, MediaType mediatype) {
            return true;
        }

        @Override
        public void write(Map<String, List<Integer>> entity, Class<?> clazz, Type genericType, MediaType mediatype,
                OutputStream out) {
        }

    }

}
