package org.nuxeo.ecm.core.io.registry.reflect;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * see {@link org.nuxeo.ecm.core.io.registry.reflect.Instanciations}
 *
 * @since 7.2
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Setup {

    /**
     * see {@link Instanciations}
     *
     * @since 7.2
     */
    Instanciations mode();

    /**
     * see {@link Priorities}
     *
     * @since 7.2
     */
    int priority() default Priorities.DEFAULT;

}
