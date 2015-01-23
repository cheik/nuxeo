package org.nuxeo.ecm.core.io.registry;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;

import javax.ws.rs.core.MediaType;

import org.apache.commons.lang3.reflect.TypeUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.io.registry.context.RenderingContext;
import org.nuxeo.ecm.core.io.registry.reflect.MarshallerInspector;
import org.nuxeo.runtime.model.ComponentContext;
import org.nuxeo.runtime.model.ComponentInstance;
import org.nuxeo.runtime.model.DefaultComponent;

public class MarshallerRegistryImpl extends DefaultComponent implements MarshallerRegistry {

    private static final Log log = LogFactory.getLog(MarshallerRegistryImpl.class);

    private static final Set<MarshallerInspector> writers = new ConcurrentSkipListSet<MarshallerInspector>();

    private static final Map<MediaType, Set<MarshallerInspector>> writersByMediaType = new ConcurrentHashMap<MediaType, Set<MarshallerInspector>>();

    private static final Map<Class<?>, MarshallerInspector> marshallersByType = new ConcurrentHashMap<Class<?>, MarshallerInspector>();

    @Override
    public void activate(ComponentContext context) {
        super.activate(context);
    }

    @Override
    public void registerContribution(Object contribution, String extensionPoint, ComponentInstance contributor) {
        if (extensionPoint.equals("marshallers")) {
            MarshallerRegistryDescriptor mrd = (MarshallerRegistryDescriptor) contribution;
            if (mrd.isEnable()) {
                register(mrd.getClazz());
            } else {
                deregister(mrd.getClazz());
            }
        }
    }

    @Override
    public void unregisterContribution(Object contribution, String extensionPoint, ComponentInstance contributor) {
        if (extensionPoint.equals("marshallers")) {
            MarshallerRegistryDescriptor mrd = (MarshallerRegistryDescriptor) contribution;
            if (mrd.isEnable()) {
                deregister(mrd.getClazz());
            } else {
                register(mrd.getClazz());
            }
        }
    }

    @Override
    public void register(Class<?> marshaller) {
        if (marshaller == null) {
            log.warn("Cannot register null marshaller");
            return;
        }
        MarshallerInspector inspector = new MarshallerInspector(marshaller);
        if (!inspector.isValid()) {
            throw new MarshallingException("Unable to register class " + marshaller.getName()
                    + " : you probably forgot the @Setup annotation or an empty public constructor.");
        }
        if (!inspector.isWriter()) {
            throw new MarshallingException(
                    "The marshaller registry just supports Writer for now. You have to implement "
                            + Writer.class.getName());
        }
        if (marshallersByType.get(marshaller) != null) {
            log.warn("The marshaller " + marshaller.getName() + " is already registered.");
            return;
        } else {
            marshallersByType.put(marshaller, inspector);
        }
        if (inspector.isWriter()) {
            writers.add(inspector);
            for (MediaType mediaType : inspector.getSupports()) {
                Set<MarshallerInspector> inspectors = writersByMediaType.get(mediaType);
                if (inspectors == null) {
                    inspectors = new ConcurrentSkipListSet<MarshallerInspector>();
                    writersByMediaType.put(mediaType, inspectors);
                }
                inspectors.add(inspector);
            }
        }
    }

    @Override
    public void deregister(Class<?> marshaller) throws MarshallingException {
        if (marshaller == null) {
            log.warn("Cannot register null marshaller");
            return;
        }
        MarshallerInspector inspector = new MarshallerInspector(marshaller);
        if (!inspector.isValid()) {
            log.warn("Unable to deregister class " + marshaller.getName()
                    + " : it seems it's not a valid marshaller (missing @Setup annotation).");
        }
        if (!inspector.isWriter()) {
            log.warn("The marshaller registry just supports Writer for now. There is no need to deregister a class which not implement "
                    + Writer.class.getName());
        }
        marshallersByType.remove(marshaller);
        if (inspector.isWriter()) {
            writers.remove(inspector);
            for (MediaType mediaType : inspector.getSupports()) {
                Set<MarshallerInspector> inspectors = writersByMediaType.get(mediaType);
                if (inspectors != null) {
                    inspectors.remove(inspector);
                }
            }
        }
    }

    @Override
    public <T> Writer<T> getWriter(RenderingContext ctx, Class<T> marshalledClazz, Type genericType, MediaType mediatype) {
        Set<MarshallerInspector> candidates = writersByMediaType.get(mediatype);
        if (candidates != null) {
            Writer<T> found = searchCandidate(ctx, marshalledClazz, genericType, mediatype, candidates);
            if (found != null) {
                return found;
            }
        }
        return searchCandidate(ctx, marshalledClazz, genericType, mediatype, writers);
    }

    private <T> Writer<T> searchCandidate(RenderingContext ctx, Class<T> marshalledClazz, Type genericType,
            MediaType mediatype, Set<MarshallerInspector> candidates) {
        for (MarshallerInspector inspector : candidates) {
            if (inspector.getMarshalledType().isAssignableFrom(marshalledClazz)) {
                if (genericType == null || marshalledClazz.equals(genericType)
                        || TypeUtils.isAssignable(genericType, inspector.getGenericType())) {
                    Writer<T> writer = inspector.getInstance(ctx);
                    if (writer.accept(marshalledClazz, genericType, mediatype)) {
                        return writer;
                    }
                }
            }
        }
        return null;
    }

    @Override
    public <T> T getInstance(RenderingContext ctx, Class<T> writerClass) {
        MarshallerInspector inspector = marshallersByType.get(writerClass);
        if (inspector == null) {
            inspector = new MarshallerInspector(writerClass);
            if (!inspector.isValid()) {
                throw new MarshallingException("Invalid marshaller class " + writerClass.getName());
            }
        }
        return inspector.getInstance(ctx);
    }

    @Override
    public void clear() {
        marshallersByType.clear();
        writersByMediaType.clear();
        writers.clear();
    }

}
