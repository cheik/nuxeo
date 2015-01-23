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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.io.registry.MarshallerRegistry;
import org.nuxeo.ecm.core.io.registry.MarshallingException;
import org.nuxeo.ecm.core.io.registry.Writer;
import org.nuxeo.ecm.core.io.registry.context.RenderingContext;
import org.nuxeo.runtime.api.Framework;

@Provider
@Produces(WILDCARD)
public final class CustomCoreIOWriterDelegate<EntityType> implements MessageBodyWriter<EntityType> {

    private static final Log log = LogFactory.getLog(CustomCoreIOWriterDelegate.class);

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

    private Writer<EntityType> writer;

    private Class<Writer<EntityType>> writerClass;

    public CustomCoreIOWriterDelegate(Class<Writer<EntityType>> writerClass) {
        super();
        this.writerClass = writerClass;
    }

    @Override
    public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        RenderingContext ctx = ctxFactory.create(request);
        try {
            writer = getRegistry().getInstance(ctx, writerClass);
        } catch (MarshallingException e) {
            log.error("Unable to instanciate the writer " + writerClass.getName(), e);
            return false;
        }
        return writer.accept(type, genericType, mediaType);
    }

    @Override
    public long getSize(EntityType t, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        return -1;
    }

    @Override
    public void writeTo(EntityType t, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType,
            MultivaluedMap<String, Object> httpHeaders, OutputStream entityStream) throws IOException,
            WebApplicationException {
        if (writer != null) {
            writer.write(t, type, genericType, mediaType, entityStream);
        }
        writer = null;
    }

}
