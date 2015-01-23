package org.nuxeo.ecm.core.io.registry.reflect;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * see {@link javax.ws.rs.core.MediaType}
 *
 * @since 7.2
 */
@Documented
@Inherited()
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Supports {

    /**
     * see {@link javax.ws.rs.core.MediaType}
     *
     * @since 7.2
     */
    String[] value();

}
