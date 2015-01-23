package org.nuxeo.ecm.core.io.registry.context;

import static org.nuxeo.ecm.core.io.registry.MarshallingConstants.ADD_ENRICHERS;
import static org.nuxeo.ecm.core.io.registry.MarshallingConstants.EMBED_PROPERTIES;
import static org.nuxeo.ecm.core.io.registry.MarshallingConstants.HEADER_PREFIX;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.nuxeo.common.utils.StringUtils;
import org.nuxeo.ecm.core.io.registry.MarshallingConstants;

public class RenderingContextImpl implements RenderingContext {

    private String baseUrl = DEFAULT_URL;

    private Locale locale = DEFAULT_LOCALE;

    private Map<String, List<Object>> parameters = new ConcurrentHashMap<String, List<Object>>();

    private Map<String, List<Object>> unModifiableParameters = null;

    private RenderingContextImpl() {
    }

    @Override
    public Locale getLocale() {
        return locale;
    }

    @Override
    public String getBaseUrl() {
        return baseUrl;
    }

    @Override
    @SuppressWarnings("deprecation")
    public Set<String> getXNParam(String name) {
        if (name == null) {
            return Collections.emptySet();
        }
        List<Object> dirty = getParameters(name);
        dirty.addAll(getParameters(HEADER_PREFIX + name));
        // backward compatibility
        if (EMBED_PROPERTIES.equals(name)) {
            dirty.addAll(getParameters(MarshallingConstants.DOCUMENT_PROPERTIES_HEADER));
        } else if (ADD_ENRICHERS.equals(name)) {
            dirty.addAll(getParameters(MarshallingConstants.NXCONTENT_CATEGORY_HEADER));
        }
        Set<String> result = new TreeSet<String>();
        for (Object value : dirty) {
            if (value instanceof String && value != null) {
                for (String cleaned : StringUtils.split((String) value, ',', true)) {
                    result.add(cleaned);
                }
            }
        }
        return result;
    }

    @Override
    public <T> T getWrappedEntity(String name) {
        return WrappedContext.getEntity(this, name);
    }

    @Override
    public <T> T getParameter(String name) {
        List<Object> values = parameters.get(name);
        if (values != null) {
            @SuppressWarnings("unchecked")
            T value = (T) values.get(0);
            return value;
        }
        return null;
    }

    @Override
    public <T> List<T> getParameters(String name) {
        @SuppressWarnings("unchecked")
        List<T> values = (List<T>) parameters.get(name);
        if (values != null) {
            return Collections.unmodifiableList(values);
        } else {
            return Collections.emptyList();
        }
    }

    @Override
    public Map<String, List<Object>> getAllParameters() {
        if (unModifiableParameters == null) {
            Map<String, List<Object>> result = new ConcurrentHashMap<String, List<Object>>();
            for (Map.Entry<String, List<Object>> entry : parameters.entrySet()) {
                String key = entry.getKey();
                List<Object> value = entry.getValue();
                if (value == null) {
                    result.put(key, null);
                } else {
                    result.put(key, Collections.unmodifiableList(value));
                }
            }
            unModifiableParameters = result;
        }
        return unModifiableParameters;
    }

    @Override
    public void setParameters(String name, Object... values) {
        if (values.length == 0) {
            parameters.remove(name);
            return;
        }
        setParameters(name, Arrays.asList(values));
    }

    @Override
    public void setParameters(String name, List<Object> values) {
        if (values == null) {
            parameters.remove(name);
        }
        parameters.put(name, new CopyOnWriteArrayList<Object>(values));
    }

    @Override
    public void addParameters(String name, Object... values) {
        addParameters(name, Arrays.asList(values));
    }

    @Override
    public void addParameters(String name, List<Object> values) {
        if (values == null) {
            return;
        }
        List<Object> currentValues = parameters.get(name);
        if (currentValues == null) {
            currentValues = new CopyOnWriteArrayList<Object>();
            parameters.put(name, currentValues);
        }
        for (Object value : values) {
            currentValues.add(value);
        }
    }

    static RenderingContextBuilder builder() {
        return new RenderingContextBuilder();
    }

    public static final class RenderingContextBuilder {

        private RenderingContextImpl ctx;

        RenderingContextBuilder() {
            ctx = new RenderingContextImpl();
        }

        public RenderingContextBuilder base(String url) {
            ctx.baseUrl = url;
            return this;
        }

        public RenderingContextBuilder charset(Locale locale) {
            ctx.locale = locale;
            return this;
        }

        public RenderingContextBuilder param(String name, Object value) {
            ctx.addParameters(name, value);
            return this;
        }

        public RenderingContextBuilder param(String name, Object[] values) {
            ctx.addParameters(name, values);
            return this;
        }

        public RenderingContextBuilder param(String name, List<Object> values) {
            ctx.addParameters(name, values);
            return this;
        }

        public RenderingContext get() {
            return ctx;
        }

    }

}
