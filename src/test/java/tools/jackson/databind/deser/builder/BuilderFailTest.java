package tools.jackson.databind.deser.builder;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.*;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.exc.InvalidDefinitionException;
import tools.jackson.databind.exc.UnrecognizedPropertyException;

import static org.junit.jupiter.api.Assertions.fail;

import static tools.jackson.databind.testutil.DatabindTestUtil.*;

public class BuilderFailTest
{
    @JsonDeserialize(builder=SimpleBuilderXY.class)
    static class ValueClassXY
    {
        final int _x, _y;

        protected ValueClassXY(int x, int y) {
            _x = x+1;
            _y = y+1;
        }
    }

    static class SimpleBuilderXY
    {
        public int x, y;

        public SimpleBuilderXY withX(int x0) {
              this.x = x0;
              return this;
        }

        public SimpleBuilderXY withY(int y0) {
              this.y = y0;
              return this;
        }

        public ValueClassXY build() {
              return new ValueClassXY(x, y);
        }
    }

    // for [databind#761]
    @JsonDeserialize(builder = ValueBuilderWrongBuildType.class)
    static class ValueClassWrongBuildType {
    }

    static class ValueBuilderWrongBuildType
    {
        public int x;

        public ValueBuilderWrongBuildType withX(int x0) {
            this.x = x0;
            return this;
        }

        public ValueClassXY build() {
            return null;
        }
    }
    /*
    /**********************************************************
    /* Unit tests
    /**********************************************************
     */

    private final ObjectMapper MAPPER = jsonMapperBuilder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

    @Test
    public void testBuilderMethodReturnInvalidType() throws Exception
    {
        final String json = "{\"x\":1}";
        try {
            MAPPER.readValue(json, ValueClassWrongBuildType.class);
            fail("Missing expected InvalidDefinitionException exception");
        } catch (InvalidDefinitionException e) {
            verifyException(e, "Build method ");
            verifyException(e, "#build()");
            verifyException(e, "has wrong return type");
        }
    }

    @Test
    public void testExtraFields() throws Exception
    {
        final String json = a2q("{'x':1,'y':2,'z':3}");
        try {
            MAPPER.readValue(json, ValueClassXY.class);
            fail("Missing expected UnrecognizedPropertyException exception");
        } catch (UnrecognizedPropertyException e) {
            verifyException(e, "Unrecognized property \"z\"");
        }
    }
}
