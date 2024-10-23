package com.wrennix.serializers;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import java.io.IOException;

public class AlphanumericRedactionSerializer extends StdSerializer<Object> {

    public AlphanumericRedactionSerializer() {
        super(Object.class);
    }

    /*
        Redacts numeric or alphanumeric type primitives, replacing numbers with the number 9 and letters with the
        letter X.
     */
    @Override
    public void serialize(Object value, JsonGenerator gen, SerializerProvider serializers) throws IOException {

        if (value instanceof Number || value instanceof String) {
            gen.writeString(String.valueOf(value).replaceAll("([A-Za-z]{1})", "X").replaceAll("(\\d)", "9"));
        } else if (value instanceof Character) {
            gen.writeString("X");
        } else {
            throw new IOException("AlphanumericRedactionSerializer cannot be used to redact value of type: " +
                value.getClass().getName());
        }
    }
}
