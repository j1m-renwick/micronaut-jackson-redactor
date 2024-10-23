package com.wrennix;

import com.wrennix.serializers.AlphanumericRedactionSerializer;
import com.wrennix.serializers.CaretRedactionSerializer;
import com.wrennix.serializers.ExesRedactionSerializer;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

public enum RedactorType {

    CARET_TEXT_REDACTION(CaretRedactionSerializer.class), // replaces with the constant '<REDACTED>'
    EXES_REDACTION(ExesRedactionSerializer.class), // replaces with the constant 'XXXX'
    ALPHANUMERIC_REDACTION(AlphanumericRedactionSerializer.class); // redacts alphanumeric type characters

    final Class<? extends StdSerializer<?>> serializer;

    RedactorType(Class<? extends StdSerializer<?>> serializer) {
        this.serializer = serializer;
    }

    public Class<? extends StdSerializer<?>> getSerializer() {
        return serializer;
    }
}
