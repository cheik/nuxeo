package org.nuxeo.ecm.core.io.registry;

import org.nuxeo.common.xmap.annotation.XNode;
import org.nuxeo.common.xmap.annotation.XObject;

@XObject("register")
public class MarshallerRegistryDescriptor {

    @XNode("@class")
    private Class<?> clazz;

    @XNode("@enable")
    private boolean enable;

    public MarshallerRegistryDescriptor() {
    }

    public MarshallerRegistryDescriptor(Class<?> clazz, boolean enable) {
        super();
        this.clazz = clazz;
        this.enable = enable;
    }

    public Class<?> getClazz() {
        return clazz;
    }

    public void setClazz(Class<?> clazz) {
        this.clazz = clazz;
    }

    public boolean isEnable() {
        return enable;
    }

    public void setEnable(boolean enable) {
        this.enable = enable;
    }

    @Override
    public String toString() {
        return clazz.getName() + ":" + Boolean.toString(enable);
    }

}
