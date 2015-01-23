package org.nuxeo.ecm.core.io.registry.context;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.nuxeo.ecm.core.io.registry.context.RenderingContextImpl.RenderingContextBuilder;

public interface RenderingContext {

    public static final Locale DEFAULT_LOCALE = Locale.ENGLISH;

    public static final String DEFAULT_URL = "http://fake-url.nuxeo.com/";

    public Locale getLocale();

    public String getBaseUrl();

    public Set<String> getXNParam(String category);

    public <T> T getWrappedEntity(String name);

    public <T> T getParameter(String name);

    public <T> List<T> getParameters(String name);

    public Map<String, List<Object>> getAllParameters();

    public void setParameters(String name, Object... values);

    public void setParameters(String name, List<Object> values);

    public void addParameters(String name, Object... values);

    public void addParameters(String name, List<Object> values);

    public static final class Builder {
        private Builder() {
        }

        public static RenderingContextBuilder builder() {
            return new RenderingContextBuilder();
        }

        public static RenderingContextBuilder base(String url) {
            RenderingContextBuilder builder = new RenderingContextBuilder();
            return builder.base(url);
        }

        public static RenderingContextBuilder charset(Locale locale) {
            RenderingContextBuilder builder = new RenderingContextBuilder();
            return builder.charset(locale);
        }

        public static RenderingContextBuilder param(String name, Object value) {
            RenderingContextBuilder builder = new RenderingContextBuilder();
            return builder.param(name, value);
        }

        public static RenderingContextBuilder param(String name, Object... values) {
            RenderingContextBuilder builder = new RenderingContextBuilder();
            return builder.param(name, values);
        }

        public static RenderingContextBuilder param(String name, List<Object> values) {
            RenderingContextBuilder builder = new RenderingContextBuilder();
            return builder.param(name, values);
        }

        public static RenderingContext get() {
            RenderingContextBuilder builder = new RenderingContextBuilder();
            return builder.get();
        }
    }

}
