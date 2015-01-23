package org.nuxeo.ecm.core.io.registry;

import org.nuxeo.ecm.core.api.ClientException;

public class MarshallingException extends ClientException {

    private static final long serialVersionUID = 1L;

    public MarshallingException() {
        super();
    }

    public MarshallingException(ClientException cause) {
        super(cause);
    }

    public MarshallingException(String message, ClientException cause) {
        super(message, cause);
    }

    public MarshallingException(String message, Throwable cause) {
        super(message, cause);
    }

    public MarshallingException(String message) {
        super(message);
    }

    public MarshallingException(Throwable cause) {
        super(cause);
    }

}
