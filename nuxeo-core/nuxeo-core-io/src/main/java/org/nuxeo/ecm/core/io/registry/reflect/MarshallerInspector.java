package org.nuxeo.ecm.core.io.registry.reflect;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.MediaType;

import org.apache.commons.lang3.reflect.TypeUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.io.registry.Writer;
import org.nuxeo.ecm.core.io.registry.context.RenderingContext;
import org.nuxeo.ecm.core.io.registry.context.RenderingContextImpl;
import org.nuxeo.ecm.core.io.registry.context.ThreadSafeRenderingContext;
import org.nuxeo.runtime.api.Framework;

import com.google.inject.Inject;

public class MarshallerInspector implements Comparable<MarshallerInspector> {

    private static final Log log = LogFactory.getLog(MarshallerInspector.class);

    private Class<?> clazz;

    private Integer priority;

    private Instanciations instanciation;

    private List<MediaType> supports = new ArrayList<MediaType>();

    private Constructor<?> constructor;

    private List<Field> serviceFields = new ArrayList<Field>();

    private List<Field> contextFields = new ArrayList<Field>();

    private Object singleton;

    private ThreadLocal<Object> threadInstance;

    private Class<?> marshalledType;

    private Type genericType;

    public MarshallerInspector(Class<?> clazz) {
        this.clazz = clazz;
        load();
    }

    public boolean isValid() {
        return instanciation != null && constructor != null && ((isWriter() && marshalledType != null) || !isWriter());
    }

    private void load() {
        for (Constructor<?> constructor : clazz.getDeclaredConstructors()) {
            if (Modifier.isPublic(constructor.getModifiers()) && constructor.getParameterTypes().length == 0) {
                this.constructor = constructor;
                break;
            }
        }
        if (constructor == null) {
            log.error("No public constructor found for class " + clazz.getName()
                    + ". Instanciation will not be possible.");
            return;
        }
        Setup setup = loadSetup(clazz);
        if (setup == null) {
            log.error("No required @Setup annotation found for class " + clazz.getName()
                    + ". Instanciation will not be possible.");
            return;
        }
        instanciation = setup.mode();
        priority = setup.priority();
        Supports supports = loadSupports(clazz);
        if (supports != null) {
            for (String mimetype : supports.value()) {
                try {
                    MediaType mediaType = MediaType.valueOf(mimetype);
                    this.supports.add(mediaType);
                } catch (IllegalArgumentException e) {
                    log.warn("In marshaller class " + clazz.getName() + ", the declared mediatype " + mimetype
                            + " cannot be parsed as a mimetype");
                }
            }
        }
        if (this.supports.isEmpty()) {
            log.warn("The marshaller " + clazz.getName()
                    + " does not support any mimetype. You can add some using annotation @Supports");
        }
        loadInjections(clazz);
        if (contextFields.size() > 1) {
            log.warn("The marshaller " + clazz.getName()
                    + " has more than one context injected property. You can check the parent class.");
        }
        if (isWriter()) {
            loadMarshalledType(clazz);
        }
    }

    private void loadMarshalledType(Class<?> clazz) {
        if (isWriter()) {
            for (Map.Entry<TypeVariable<?>, Type> entry : TypeUtils.getTypeArguments(clazz, Writer.class).entrySet()) {
                if (Writer.class.equals(entry.getKey().getGenericDeclaration())) {
                    Type value = entry.getValue();
                    if (value instanceof Class) {
                        marshalledType = (Class<?>) value;
                        return;
                    }
                }
            }
        }
    }

    private Setup loadSetup(Class<?> clazz) {
        if (Object.class.equals(clazz)) {
            return null;
        }
        return clazz.getAnnotation(Setup.class);
    }

    private Supports loadSupports(Class<?> clazz) {
        if (Object.class.equals(clazz)) {
            return null;
        }
        Supports supports = clazz.getAnnotation(Supports.class);
        if (supports != null) {
            return supports;
        } else {
            return loadSupports(clazz.getSuperclass());
        }
    }

    private void loadInjections(Class<?> clazz) {
        if (Object.class.equals(clazz)) {
            return;
        }
        for (Field field : clazz.getDeclaredFields()) {
            if (field.isAnnotationPresent(Inject.class)) {
                if (RenderingContext.class.equals(field.getType())) {
                    field.setAccessible(true);
                    contextFields.add(field);
                } else {
                    field.setAccessible(true);
                    serviceFields.add(field);
                }
            }
        }
        loadInjections(clazz.getSuperclass());
    }

    @SuppressWarnings("unchecked")
    public <T> T getInstance(RenderingContext ctx) {
        if (!isValid()) {
            log.warn("The MarshallerInspector managing the class " + clazz.getName()
                    + " cannot instanciate it, please fix previous problems.");
            return null;
        }
        RenderingContext realCtx = getRealContext(ctx);
        switch (instanciation) {
        case SINGLETON:
            return (T) getSingletonInstance(realCtx);
        case PER_THREAD:
            return (T) getThreadInstance(realCtx);
        case EACH_TIME:
            return (T) getNewInstance(realCtx, false);
        default:
            return null;
        }
    }

    private RenderingContext getRealContext(RenderingContext ctx) {
        if (ctx == null) {
            return RenderingContext.Builder.get();
        }
        if (ctx instanceof RenderingContextImpl) {
            return ctx;
        }
        if (ctx instanceof ThreadSafeRenderingContext) {
            RenderingContext delegate = ((ThreadSafeRenderingContext) ctx).getDelegate();
            return getRealContext(delegate);
        }
        return null;
    }

    private Object getSingletonInstance(RenderingContext ctx) {
        if (singleton == null) {
            singleton = getNewInstance(ctx, true);
        } else {
            for (Field contextField : contextFields) {
                ThreadSafeRenderingContext value;
                try {
                    value = (ThreadSafeRenderingContext) contextField.get(singleton);
                } catch (IllegalArgumentException | IllegalAccessException e) {
                    log.error("unable to create a marshaller instance for clazz " + clazz.getName(), e);
                    return null;
                }
                value.configureThread(ctx);
            }
        }
        return singleton;
    }

    private Object getThreadInstance(RenderingContext ctx) {
        if (threadInstance == null) {
            threadInstance = new ThreadLocal<Object>();
        }
        Object instance = threadInstance.get();
        if (instance == null) {
            instance = getNewInstance(ctx, false);
            threadInstance.set(instance);
        } else {
            for (Field contextField : contextFields) {
                try {
                    contextField.set(instance, ctx);
                } catch (IllegalArgumentException | IllegalAccessException e) {
                    log.error("unable to create a marshaller instance for clazz " + clazz.getName(), e);
                    return null;
                }
            }
        }
        return instance;
    }

    private Object getNewInstance(RenderingContext ctx, boolean threadSafe) {
        try {
            Object instance = clazz.newInstance();
            for (Field contextField : contextFields) {
                if (threadSafe) {
                    ThreadSafeRenderingContext safeCtx = new ThreadSafeRenderingContext();
                    safeCtx.configureThread(ctx);
                    contextField.set(instance, safeCtx);
                } else {
                    contextField.set(instance, ctx);
                }
            }
            for (Field serviceField : serviceFields) {
                Object service = Framework.getService(serviceField.getType());
                if (service == null) {
                    log.error("unable to inject a service " + serviceField.getType().getName()
                            + " in the marshaller clazz " + clazz.getName());
                    return null;
                }
                serviceField.set(instance, service);
            }
            return instance;
        } catch (IllegalArgumentException | IllegalAccessException | InstantiationException e) {
            log.error("unable to create a marshaller instance for clazz " + clazz.getName(), e);
            return null;
        }
    }

    public Instanciations getInstanciations() {
        return instanciation;
    }

    public Integer getPriority() {
        return priority;
    }

    public List<MediaType> getSupports() {
        return supports;
    }

    public Class<?> getMarshalledType() {
        return marshalledType;
    }

    public Type getGenericType() {
        return genericType;
    }

    public boolean isWriter() {
        return Writer.class.isAssignableFrom(clazz);
    }

    @Override
    public int compareTo(MarshallerInspector inspector) {
        if (inspector != null) {
            int result = getPriority().compareTo(inspector.getPriority());
            if (result != 0) {
                return -result;
            }
            result = getInstanciations().compareTo(inspector.getInstanciations());
            if (result != 0) {
                return -result;
            }
            // force sub classes to manage their priorities
            if (!clazz.equals(inspector.clazz)) {
                if (clazz.isAssignableFrom(inspector.clazz)) {
                    return -1;
                } else if (inspector.clazz.isAssignableFrom(clazz)) {
                    return 1;
                }
            }
            if (isWriter() && inspector.isWriter()) {
                result = getMarshalledType().getClass().getName().compareTo(
                        inspector.getMarshalledType().getClass().getName());
                if (result != 0) {
                    return -result;
                }
            }
            return -clazz.getName().compareTo(inspector.clazz.getName());
        }
        return 1;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof MarshallerInspector)) {
            return false;
        }
        MarshallerInspector inspector = (MarshallerInspector) obj;
        if (clazz != null) {
            return clazz.equals(inspector.clazz);
        } else {
            if (inspector.clazz == null) {
                return true;
            }
        }
        return false;
    }

}
