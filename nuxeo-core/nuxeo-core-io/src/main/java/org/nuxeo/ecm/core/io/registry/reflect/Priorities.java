package org.nuxeo.ecm.core.io.registry.reflect;

public interface Priorities {

    int TECHNICAL = -1000;

    int DEFAULT = 0;

    int DERIVATIVE = 1000;

    int REFERENCE = 2000;

    int OVERRIDE_REFERENCE = 3000;

}
