package org.nuxeo.ecm.core.io.registry;

import java.lang.reflect.Type;

import javax.ws.rs.core.MediaType;

import org.nuxeo.ecm.core.io.registry.context.RenderingContext;

public interface MarshallerRegistry {

    public void clear();

    public void register(Class<?> marshaller) throws MarshallingException;

    public void deregister(Class<?> marshaller) throws MarshallingException;

    public <T> Writer<T> getWriter(RenderingContext ctx, Class<T> marshalledClazz, Type genericType, MediaType mediatype);

    public <T> T getInstance(RenderingContext ctx, Class<T> writerClass);

}
