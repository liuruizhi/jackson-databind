package tools.jackson.databind.convert;

import java.lang.reflect.Array;
import java.math.*;
import java.util.*;

import org.junit.jupiter.api.Test;

import tools.jackson.core.exc.InputCoercionException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.*;

import static org.junit.jupiter.api.Assertions.*;

import static tools.jackson.databind.testutil.DatabindTestUtil.*;

public class ArrayConversionsTest
{
    final static String OVERFLOW_MSG_BYTE = "out of range of `byte`";
    final static String OVERFLOW_MSG_SHORT = "out of range of `short`";
    final static String OVERFLOW_MSG_INT = "out of range of `int`";
    final static String OVERFLOW_MSG_LONG = "out of range of `long`";

    final ObjectMapper MAPPER = newJsonMapper();

    @Test
    public void testNullXform() throws Exception
    {
        /* when given null, null should be returned without conversion
         * (Java null has no type)
         */
        assertNull(MAPPER.convertValue(null, Integer.class));
        assertNull(MAPPER.convertValue(null, String.class));
        assertNull(MAPPER.convertValue(null, byte[].class));
    }

    /**
     * Tests to verify that primitive number arrays round-trip
     * correctly, i.e. type -> type gives equal (although
     * not necessarily same) output
     */
    @Test
    public void testArrayIdentityTransforms() throws Exception
    {
        // first integral types
        // (note: byte[] is ok, even if it goes to base64 and back)
        verifyByteArrayConversion(bytes(), byte[].class);
        verifyShortArrayConversion(shorts(), short[].class);
        verifyIntArrayConversion(ints(), int[].class);
        verifyLongArrayConversion(longs(), long[].class);
        // then primitive decimal types
        verifyFloatArrayConversion(floats(), float[].class);
        verifyDoubleArrayConversion(doubles(), float[].class);
    }

    @Test
    public void testByteArrayFrom() throws Exception
    {
        /* Note: byte arrays are tricky, since they are considered
         * binary data primarily, not as array of numbers. Hence
         * output will be base64 encoded...
         */
        byte[] data = _convert("c3VyZS4=", byte[].class);
        byte[] exp = "sure.".getBytes("Ascii");
        verifyIntegralArrays(exp, data, exp.length);
    }

    @Test
    public void testShortArrayToX() throws Exception
    {
        short[] data = shorts();
        verifyShortArrayConversion(data, byte[].class);
        verifyShortArrayConversion(data, int[].class);
        verifyShortArrayConversion(data, long[].class);
    }

    @Test
    public void testIntArrayToX() throws Exception
    {
        int[] data = ints();
        verifyIntArrayConversion(data, byte[].class);
        verifyIntArrayConversion(data, short[].class);
        verifyIntArrayConversion(data, long[].class);

        List<Number> expNums = _numberList(data, data.length);
        // Alas, due to type erasure, need to use TypeRef, not just class
        List<Integer> actNums = MAPPER.convertValue(data, new TypeReference<List<Integer>>() {});
        assertEquals(expNums, actNums);
    }

    @Test
    public void testLongArrayToX() throws Exception
    {
        long[] data = longs();
        verifyLongArrayConversion(data, byte[].class);
        verifyLongArrayConversion(data, short[].class);
        verifyLongArrayConversion(data, int[].class);

        List<Number> expNums = _numberList(data, data.length);
        List<Long> actNums = MAPPER.convertValue(data, new TypeReference<List<Long>>() {});
        assertEquals(expNums, actNums);
    }

    @Test
    public void testOverflows()
    {
        // Byte overflow
        try {
            MAPPER.convertValue(new int[] { 1000 }, byte[].class);
            fail("Expected an exception");
        } catch (InputCoercionException e) {
            // 16-Jan-2021, tatu: not sure what is ideal as the underlying source
            //    exception is streaming `InputCoercionException`

            verifyException(e, OVERFLOW_MSG_BYTE);
        }

        // Short overflow
        try {
            MAPPER.convertValue(new int[] { -99999 }, short[].class);
            fail("Expected an exception");
        } catch (InputCoercionException e) {
            verifyException(e, OVERFLOW_MSG_SHORT);
        }
        // Int overflow
        try {
            MAPPER.convertValue(new long[] { Long.MAX_VALUE }, int[].class);
            fail("Expected an exception");
        } catch (InputCoercionException e) {
            verifyException(e, OVERFLOW_MSG_INT);
        }
        // Longs need help of BigInteger...
        BigInteger biggie = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE);
        List<BigInteger> l = new ArrayList<BigInteger>();
        l.add(biggie);
        try {
            MAPPER.convertValue(l, long[].class);
            fail("Expected an exception");
        } catch (InputCoercionException e) {
            verifyException(e, OVERFLOW_MSG_LONG);
        }
    }

    /*
    /**********************************************************************
    /* Helper methods
    /**********************************************************************
     */

    // note: all value need to be within byte range

    private byte[] bytes() { return new byte[] { 1, -1, 0, 98, 127 }; }
    private short[] shorts() { return new short[] { 1, -1, 0, 98, 127 }; }
    private int[] ints() { return new int[] { 1, -1, 0, 98, 127 }; }
    private long[] longs() { return new long[] { 1, -1, 0, 98, 127 }; }

    // note: use values that are exact in binary

    private double[] doubles() { return new double[] { 0.0, 0.25, -0.125, 10.5, 9875.0 }; }
    private float[] floats() { return new float[] {
            0.0f, 0.25f, -0.125f, 10.5f, 9875.0f };
    }

    private <T> void verifyByteArrayConversion(byte[] data, Class<T> arrayType) {
        T result = _convert(data, arrayType);
        verifyIntegralArrays(data, result, data.length);
    }
    private <T> void verifyShortArrayConversion(short[] data, Class<T> arrayType) {
        T result = _convert(data, arrayType);
        verifyIntegralArrays(data, result, data.length);
    }
    private <T> void verifyIntArrayConversion(int[] data, Class<T> arrayType) {
        T result = _convert(data, arrayType);
        verifyIntegralArrays(data, result, data.length);
    }
    private <T> void verifyLongArrayConversion(long[] data, Class<T> arrayType) {
        T result = _convert(data, arrayType);
        verifyIntegralArrays(data, result, data.length);
    }
    private <T> void verifyFloatArrayConversion(float[] data, Class<T> arrayType) {
        T result = _convert(data, arrayType);
        verifyDoubleArrays(data, result, data.length);
    }
    private <T> void verifyDoubleArrayConversion(double[] data, Class<T> arrayType) {
        T result = _convert(data, arrayType);
        verifyDoubleArrays(data, result, data.length);
    }

    private <T> T _convert(Object input, Class<T> outputType)
    {
        // must be a primitive array, like "int[].class"
        if (!outputType.isArray()) throw new IllegalArgumentException();
        if (!outputType.getComponentType().isPrimitive()) throw new IllegalArgumentException();
        T result = MAPPER.convertValue(input, outputType);
        // sanity check first:
        assertNotNull(result);
        assertEquals(outputType, result.getClass());
        return result;
    }

    private List<Number> _numberList(Object numberArray, int size)
    {
        ArrayList<Number> result = new ArrayList<Number>(size);
        for (int i = 0; i < size; ++i) {
            result.add((Number) Array.get(numberArray, i));
        }
        return result;
    }

    /**
     * Helper method for checking that given collections contain integral Numbers
     * that essentially contain same values in same order
     */
    private void verifyIntegralArrays(Object inputArray, Object outputArray, int size)
    {
        for (int i = 0; i < size; ++i) {
            Number n1 = (Number) Array.get(inputArray, i);
            Number n2 = (Number) Array.get(outputArray, i);
            double value1 = n1.longValue();
            double value2 = n2.longValue();
            assertEquals(value1, value2, "Entry #"+i+"/"+size+" not equal");
        }
    }

    private void verifyDoubleArrays(Object inputArray, Object outputArray, int size)
    {
        for (int i = 0; i < size; ++i) {
            Number n1 = (Number) Array.get(inputArray, i);
            Number n2 = (Number) Array.get(outputArray, i);
            double value1 = n1.doubleValue();
            double value2 = n2.doubleValue();
            assertEquals(value1, value2, "Entry #"+i+"/"+size+" not equal");
        }
    }
}
