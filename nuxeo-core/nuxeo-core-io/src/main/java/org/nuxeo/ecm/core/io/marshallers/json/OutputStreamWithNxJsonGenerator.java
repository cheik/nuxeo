package org.nuxeo.ecm.core.io.marshallers.json;

import java.io.IOException;
import java.io.OutputStream;

public class OutputStreamWithNxJsonGenerator extends OutputStream {

    private OutputStream out;

    private NxJsonGenerator jsonGenerator;

    public OutputStreamWithNxJsonGenerator(NxJsonGenerator jsonGenerator) {
        super();
        this.jsonGenerator = jsonGenerator;
        out = jsonGenerator.getOutputStream();
    }

    public NxJsonGenerator getNxJsonGenerator() {
        return jsonGenerator;
    }

    @Override
    public void write(int b) throws IOException {
        out.write(b);
    }

    @Override
    public void write(byte[] b) throws IOException {
        out.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        out.write(b, off, len);
    }

    @Override
    public boolean equals(Object obj) {
        return out.equals(obj);
    }

    @Override
    public void flush() throws IOException {
        out.flush();
    }

    @Override
    public void close() throws IOException {
        out.close();
    }

}
