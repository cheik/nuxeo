package org.nuxeo.ecm.webengine.jaxrs.marshallers;

import javax.servlet.http.HttpServletRequest;

import org.nuxeo.ecm.core.io.registry.context.RenderingContext;

public class RenderingContextFactory {

    public RenderingContext create(HttpServletRequest request) {
        // TODO fill the context
        return RenderingContext.Builder.get();
    }

}
