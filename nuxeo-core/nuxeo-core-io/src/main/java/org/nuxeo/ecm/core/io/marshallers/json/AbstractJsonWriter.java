package org.nuxeo.ecm.core.io.marshallers.json;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON_TYPE;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Type;

import javax.ws.rs.core.MediaType;

import org.nuxeo.ecm.core.io.registry.MarshallerRegistry;
import org.nuxeo.ecm.core.io.registry.Writer;
import org.nuxeo.ecm.core.io.registry.context.RenderingContext;
import org.nuxeo.ecm.core.io.registry.reflect.Supports;

import com.google.inject.Inject;

@Supports(APPLICATION_JSON)
public abstract class AbstractJsonWriter<EntityType> implements Writer<EntityType> {

    @Inject
    protected RenderingContext ctx;

    @Inject
    protected MarshallerRegistry registry;

    @Override
    public boolean accept(Class<?> clazz, Type genericType, MediaType mediatype) {
        return true;
    }

    @Override
    public void write(EntityType entity, Class<?> clazz, Type genericType, MediaType mediatype, OutputStream out)
            throws IOException {
        NxJsonGenerator jg = getGenerator(out, true);
        write(entity, clazz, genericType, mediatype, jg);
    }

    public abstract void write(EntityType entity, Class<?> clazz, Type genericType, MediaType mediatype,
            NxJsonGenerator jg) throws IOException;

    protected void writeEntityField(String fieldName, Object entity, NxJsonGenerator jg)
            throws IOException {
        jg.writeFieldName(fieldName);
        writeEntity(entity, jg);
    }

    protected void writeEntity(Object entity, NxJsonGenerator jg) throws IOException {
        OutputStream out = new OutputStreamWithNxJsonGenerator(jg);
        writeEntity(entity, out);
    }

    protected <ObjectType> void writeEntity(ObjectType entity, OutputStream out) throws IOException {
        @SuppressWarnings("unchecked")
        Class<ObjectType> clazz = (Class<ObjectType>) entity.getClass();
        Writer<ObjectType> writer = registry.getWriter(ctx, clazz, null, APPLICATION_JSON_TYPE);
        writer.write(entity, entity.getClass(), null, APPLICATION_JSON_TYPE, out);
    }

    protected NxJsonGenerator getGenerator(OutputStream out, boolean getCurrentIfAvailable) throws IOException {
        if (out instanceof OutputStreamWithNxJsonGenerator) {
            OutputStreamWithNxJsonGenerator casted = (OutputStreamWithNxJsonGenerator) out;
            NxJsonGenerator jsonGenerator = casted.getNxJsonGenerator();
            if (getCurrentIfAvailable) {
                return jsonGenerator;
            } else {
                return new NxJsonGenerator(jsonGenerator.getOutputStream());
            }
        }
        return new NxJsonGenerator(out);
    }

}
