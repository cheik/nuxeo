package org.nuxeo.ecm.webengine.jaxrs.marshallers;

import static javax.ws.rs.core.MediaType.WILDCARD;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;

import org.nuxeo.ecm.core.io.registry.MarshallerRegistry;
import org.nuxeo.ecm.core.io.registry.Writer;
import org.nuxeo.ecm.core.io.registry.context.RenderingContext;
import org.nuxeo.runtime.api.Framework;

@Provider
@Produces(WILDCARD)
public final class FullCoreIOWriterDelegate implements MessageBodyWriter<Object> {

    public static MarshallerRegistry registry;

    public static MarshallerRegistry getRegistry() {
        if (registry == null) {
            registry = Framework.getService(MarshallerRegistry.class);
        }
        return registry;
    }

    public static RenderingContextFactory ctxFactory = new RenderingContextFactory();

    @Context
    private HttpServletRequest request;

    private Writer<?> writer = null;

    @Override
    public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        RenderingContext ctx = ctxFactory.create(request);
        writer = getRegistry().getWriter(ctx, type, genericType, mediaType);
        return writer != null;
    }

    @Override
    public long getSize(Object t, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        return -1;
    }

    @Override
    public void writeTo(Object t, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType,
            MultivaluedMap<String, Object> httpHeaders, OutputStream entityStream) throws IOException,
            WebApplicationException {
        if (writer != null) {
            writerWriteTo(t, type, genericType, mediaType, entityStream);
        }
    }

    private <T> void writerWriteTo(T t, Class<?> type, Type genericType, MediaType mediaType, OutputStream entityStream)
            throws IOException {
        @SuppressWarnings("unchecked")
        Writer<T> casted = (Writer<T>) writer;
        casted.write(t, type, genericType, mediaType, entityStream);
    }

}
