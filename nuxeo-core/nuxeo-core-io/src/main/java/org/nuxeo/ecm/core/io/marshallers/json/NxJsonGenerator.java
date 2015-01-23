package org.nuxeo.ecm.core.io.marshallers.json;

import static org.nuxeo.ecm.core.io.registry.MarshallingConstants.ENTITY_FIELD_NAME;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;

import org.codehaus.jackson.JsonEncoding;
import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.JsonProcessingException;
import org.codehaus.jackson.map.ObjectMapper;

public class NxJsonGenerator {

    private static JsonFactory jsonFactory = null;

    private static JsonFactory getJsonFactory() {
        if (jsonFactory == null) {
            jsonFactory = new JsonFactory(new ObjectMapper());
        }
        return jsonFactory;
    }

    private JsonGenerator jg;

    private OutputStream out;

    public NxJsonGenerator(OutputStream out) throws IOException {
        super();
        this.out = out;
        jg = getJsonFactory().createJsonGenerator(out, JsonEncoding.UTF8);
    }

    public JsonGenerator getDelegate() {
        return jg;
    }

    public OutputStream getOutputStream() {
        return out;
    }

    public void writeEntityTypeField(String type) throws JsonGenerationException, IOException {
        writeStringField(ENTITY_FIELD_NAME, type);
    }

    public void writeStartArray() throws IOException, JsonGenerationException {
        jg.writeStartArray();
    }

    public void writeEndArray() throws IOException, JsonGenerationException {
        jg.writeEndArray();
    }

    public void writeStartObject() throws IOException, JsonGenerationException {
        jg.writeStartObject();
    }

    public void writeEndObject() throws IOException, JsonGenerationException {
        jg.writeEndObject();
    }

    public void writeFieldName(String name) throws IOException, JsonGenerationException {
        jg.writeFieldName(name);
    }

    public void writeString(String text) throws IOException, JsonGenerationException {
        jg.writeString(text);
    }

    public void writeRaw(String text) throws IOException, JsonGenerationException {
        jg.writeRaw(text);
    }

    public void writeRawValue(String text) throws IOException, JsonGenerationException {
        jg.writeRawValue(text);
    }

    public void writeNumber(int v) throws IOException, JsonGenerationException {
        jg.writeNumber(v);
    }

    public void writeNumber(long v) throws IOException, JsonGenerationException {
        jg.writeNumber(v);
    }

    public void writeNumber(BigInteger v) throws IOException, JsonGenerationException {
        jg.writeNumber(v);
    }

    public void writeNumber(double d) throws IOException, JsonGenerationException {
        jg.writeNumber(d);
    }

    public void writeNumber(float f) throws IOException, JsonGenerationException {
        jg.writeNumber(f);
    }

    public void writeNumber(BigDecimal dec) throws IOException, JsonGenerationException {
        jg.writeNumber(dec);
    }

    public void writeBoolean(boolean state) throws IOException, JsonGenerationException {
        jg.writeBoolean(state);
    }

    public void writeNull() throws IOException, JsonGenerationException {
        jg.writeNull();
    }

    public void writeObject(Object pojo) throws IOException, JsonProcessingException {
        jg.writeObject(pojo);
    }

    public void writeTree(JsonNode rootNode) throws IOException, JsonProcessingException {
        jg.writeTree(rootNode);
    }

    public void writeStringField(String fieldName, String value) throws IOException, JsonGenerationException {
        jg.writeStringField(fieldName, value);
    }

    public final void writeBooleanField(String fieldName, boolean value) throws IOException, JsonGenerationException {
        jg.writeBooleanField(fieldName, value);
    }

    public final void writeNullField(String fieldName) throws IOException, JsonGenerationException {
        jg.writeNullField(fieldName);
    }

    public final void writeNumberField(String fieldName, int value) throws IOException, JsonGenerationException {
        jg.writeNumberField(fieldName, value);
    }

    public final void writeNumberField(String fieldName, long value) throws IOException, JsonGenerationException {
        jg.writeNumberField(fieldName, value);
    }

    public final void writeNumberField(String fieldName, double value) throws IOException, JsonGenerationException {
        jg.writeNumberField(fieldName, value);
    }

    public final void writeNumberField(String fieldName, float value) throws IOException, JsonGenerationException {
        jg.writeNumberField(fieldName, value);
    }

    public final void writeNumberField(String fieldName, BigDecimal value) throws IOException, JsonGenerationException {
        jg.writeNumberField(fieldName, value);
    }

    public final void writeArrayFieldStart(String fieldName) throws IOException, JsonGenerationException {
        jg.writeArrayFieldStart(fieldName);
    }

    public final void writeObjectFieldStart(String fieldName) throws IOException, JsonGenerationException {
        jg.writeObjectFieldStart(fieldName);
    }

    public final void writeObjectField(String fieldName, Object pojo) throws IOException, JsonProcessingException {
        jg.writeObjectField(fieldName, pojo);
    }

    public void flush() throws IOException {
        jg.flush();
    }

    public boolean isClosed() {
        return jg.isClosed();
    }

    public void close() throws IOException {
        jg.close();
    }

}
