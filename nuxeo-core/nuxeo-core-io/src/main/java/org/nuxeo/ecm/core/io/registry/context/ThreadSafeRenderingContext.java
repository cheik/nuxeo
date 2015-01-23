package org.nuxeo.ecm.core.io.registry.context;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ThreadSafeRenderingContext implements RenderingContext {

    private final ThreadLocal<RenderingContext> ctx = new ThreadLocal<RenderingContext>();

    public ThreadSafeRenderingContext() {
    }

    public void configureThread(RenderingContext delegates) {
        ctx.set(delegates);
    }

    public RenderingContext getDelegate() {
        return ctx.get();
    }

    @Override
    public Locale getLocale() {
        return ctx.get().getLocale();
    }

    @Override
    public String getBaseUrl() {
        return ctx.get().getBaseUrl();
    }

    @Override
    public Set<String> getXNParam(String category) {
        return ctx.get().getXNParam(category);
    }

    @Override
    public <T> T getWrappedEntity(String name) {
        return ctx.get().getWrappedEntity(name);
    }

    @Override
    public <T> T getParameter(String name) {
        return ctx.get().getParameter(name);
    }

    @Override
    public <T> List<T> getParameters(String name) {
        return ctx.get().getParameters(name);
    }

    @Override
    public Map<String, List<Object>> getAllParameters() {
        return ctx.get().getAllParameters();
    }

    @Override
    public void setParameters(String name, Object... values) {
        ctx.get().setParameters(name, values);
    }

    @Override
    public void setParameters(String name, List<Object> values) {
        ctx.get().setParameters(name, values);
    }

    @Override
    public void addParameters(String name, Object... values) {
        ctx.get().addParameters(name, values);
    }

    @Override
    public void addParameters(String name, List<Object> values) {
        ctx.get().addParameters(name, values);
    }

}
