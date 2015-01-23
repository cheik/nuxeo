package org.nuxeo.ecm.core.io.registry.context;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

public class TestRenderingContext {

    private static final String VALUE1 = "value";

    private static final String VALUE2 = "value";

    private static final String PARAM = "test";

    @Test
    public void emptyContext() throws Exception {
        RenderingContext ctx = RenderingContext.Builder.get();
        assertEquals(RenderingContext.DEFAULT_URL, ctx.getBaseUrl());
        assertEquals(RenderingContext.DEFAULT_LOCALE, ctx.getLocale());
        assertTrue(ctx.getAllParameters().isEmpty());
    }

    @Test
    public void canSetAndGetSimpleParameter() throws Exception {
        RenderingContext ctx = RenderingContext.Builder.param(PARAM, VALUE1).get();
        assertEquals(VALUE1, ctx.getParameter(PARAM));
    }

    @Test
    public void canSetAndGetMultipleParameters() throws Exception {
        RenderingContext ctx = RenderingContext.Builder.param(PARAM, VALUE1, VALUE2).get();
        List<String> list = ctx.getParameters(PARAM);
        assertEquals(2, list.size());
        assertTrue(list.contains(VALUE1));
        assertTrue(list.contains(VALUE2));
    }

}
