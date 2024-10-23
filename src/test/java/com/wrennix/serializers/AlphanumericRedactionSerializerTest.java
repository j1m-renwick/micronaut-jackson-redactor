package com.wrennix.serializers;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class AlphanumericRedactionSerializerTest {

    AlphanumericRedactionSerializer underTest;
    JsonGenerator generator;
    ByteArrayOutputStream outputStream;

    @BeforeEach
    void setup() {
        underTest = new AlphanumericRedactionSerializer();
        outputStream = new ByteArrayOutputStream();
        try {
            generator = new ObjectMapper().writer().createGenerator(outputStream);
        } catch (IOException e) {
            Assertions.fail("Failed to instantiate JsonGenerator: " + e.getMessage(), e);
        }

    }

    private static Stream<Arguments> inputParameters() {
        return Stream.of(
            Arguments.of(Integer.MAX_VALUE, "9999999999"),
            Arguments.of(Long.MAX_VALUE, "9999999999999999999"),
            Arguments.of(Short.MAX_VALUE, "99999"),
            Arguments.of(Double.valueOf("123142.1324223"), "999999.9999999"),
            Arguments.of(Float.valueOf("123.45678"), "999.99999"),
            Arguments.of(Character.valueOf('c'), "X"),
            Arguments.of('c', "X"),
            Arguments.of("hêre is some tęxt!", "XêXX XX XXXX XęXX!"),
            Arguments.of("hêre 2 is some1's tęxt!", "XêXX 9 XX XXXX9'X XęXX!"),
            Arguments.of("12345", "99999")
            );
    }

    @ParameterizedTest
    @MethodSource("inputParameters")
    void inputIsRedactedAsExpected(Object input, String expected) {
        try {
            underTest.serialize(input, generator, null);
            generator.flush();
        } catch (IOException e) {
            Assertions.fail("Call to serialize failed: " + e.getMessage(), e);
        }

        Assertions.assertEquals("\"" + expected + "\"", outputStream.toString());
    }

    @Test
    void unsupportedInputThrowsException() {
        try {
            underTest.serialize(new Throwable(), generator, null);
            Assertions.fail("Should not get to here without failing!");
        } catch (IOException e) {
            Assertions.assertEquals("AlphanumericRedactionSerializer cannot be used " +
                "to redact value of type: java.lang.Throwable", e.getMessage());
        }

    }

}
