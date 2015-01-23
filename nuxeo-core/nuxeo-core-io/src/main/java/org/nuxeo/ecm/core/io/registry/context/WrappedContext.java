/*
 * (C) Copyright 2014 Nuxeo SA (http://nuxeo.com/) and contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl-2.1.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * Contributors:
 *     nchapurlatn <nc@nuxeo.com>
 */

package org.nuxeo.ecm.core.io.registry.context;

import static org.nuxeo.ecm.core.io.registry.MarshallingConstants.WRAPPED_CONTEXT;

import java.io.Closeable;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletRequest;

import org.nuxeo.ecm.core.io.registry.MarshallingException;

/**
 * Provides a way to create wrapped contexts containing entities. This allow to manage hierarchical contexts.
 * <p>
 * First, create the context, fill it and then use a try-resource statement to ensure the context will be closed.
 * </p>
 *
 * <pre>
 * <code>
 * WrappedContext documentContext = new WrappedContext(request);
 * DocumentModel doc = ...;
 * documentContext.push(EntityTypes.DOCUMENT, doc);
 * try (Closeable ctx = documentContext.open()) {
 *   // the document will only be available in the following statements using WrappedContext.getEntity(request, EntityTypes.DOCUMENT)
 *   writeMarshallable(jg, prop, Property.class, httpHeaders);
 * }
 * </code>
 * </pre>
 * <p>
 * To get contextual entity, use {@link WrappedContext#getEntity(ServletRequest, String)}. If no context are available,
 * null is returned.
 * </p>
 * <p>
 * Note that if in the try-resource statement, another context is created, the entity will be searched first in this
 * context, if not found, recursively in the parent context: the nearest will be returned.
 * </p>
 *
 * @since 7.2
 */
public final class WrappedContext {

    private WrappedContext parent;

    private RenderingContext ctx;

    private Map<String, Object> entries = new HashMap<String, Object>();

    private WrappedContext(RenderingContext ctx) {
        if (ctx == null) {
            throw new MarshallingException("Cannot get a wrapped context without RenderingContext");
        }
        this.ctx = ctx;
        parent = ctx.getParameter(WRAPPED_CONTEXT);
    }

    private static WrappedContext get(RenderingContext ctx) {
        if (ctx != null) {
            return ctx.getParameter(WRAPPED_CONTEXT);
        } else {
            throw new MarshallingException("Cannot get a wrapped context without RenderingContext");
        }
    }

    public static WrappedContext create(RenderingContext ctx) {
        if (ctx != null) {
            WrappedContext child = new WrappedContext(ctx);
            return child;
        } else {
            throw new MarshallingException("Cannot get a wrapped context without RenderingContext");
        }
    }

    /**
     * Add entity to the context.
     *
     * @param entityType The string used to get the entity.
     * @param value The entity.
     * @since 7.2
     */
    public final WrappedContext with(String entityType, Object value) {
        entries.put(entityType, value);
        return this;
    }

    /**
     * Provides a flatten map of wrapped contexts. If a same entity type is stored in multiple contexts, the nearest one
     * will be returned.
     *
     * @since 7.2
     */
    public final Map<String, Object> flatten() {
        Map<String, Object> mergedResult = new HashMap<String, Object>();
        if (parent != null) {
            mergedResult.putAll(parent.flatten());
        }
        mergedResult.putAll(entries);
        return mergedResult;
    }

    /**
     * Gets the nearest entity stored in the wrapped contexts.
     *
     * @param request The request where contexts are stored.
     * @param entityType The entity type to get.
     * @since 7.2
     */
    static <T> T getEntity(RenderingContext ctx, String entityType) {
        T value = null;
        WrappedContext wrappedCtx = get(ctx);
        if (wrappedCtx != null) {
            return wrappedCtx.innerGetEntity(entityType);
        }
        return value;
    }

    /**
     * Recursive search for the nearest entity.
     *
     * @since 7.2
     */
    private final <T> T innerGetEntity(String entityType) {
        @SuppressWarnings("unchecked")
        T value = (T) entries.get(entityType);
        if (value == null && parent != null) {
            return parent.innerGetEntity(entityType);
        }
        return value;
    }

    /**
     * Open the context and make all embedded entities available. Returns a {@link Closeable} which must be closed at
     * the end.
     * <p>
     * Note the same context could be opened and closed several times.
     * </p>
     *
     * @return A {@link Closeable} instance.
     * @since 7.2
     */
    public final Closeable open() {
        ctx.setParameters(WRAPPED_CONTEXT, this);
        return new Closeable() {
            @Override
            public void close() throws IOException {
                ctx.setParameters(WRAPPED_CONTEXT, parent);
            }
        };
    }

    @Override
    public String toString() {
        return flatten().toString();
    }

}
