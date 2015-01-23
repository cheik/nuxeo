package org.nuxeo.ecm.core.io.registry;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Type;

import javax.ws.rs.core.MediaType;

public interface Writer<EntityType> {

    public boolean accept(Class<?> clazz, Type genericType, MediaType mediatype);

    public void write(EntityType entity, Class<?> clazz, Type genericType, MediaType mediatype, OutputStream out)
            throws IOException;

}
