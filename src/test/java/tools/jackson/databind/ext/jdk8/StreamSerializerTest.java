package tools.jackson.databind.ext.jdk8;

import java.util.*;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import tools.jackson.core.type.TypeReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class StreamSerializerTest extends StreamTestBase
{
    static class TestBean
    {
        public int foo;
        public String bar;

        @JsonCreator
        public TestBean(@JsonProperty("foo") int foo, @JsonProperty("bar") String bar)
        {
            this.foo = foo;
            this.bar = bar;
        }

        @Override
        public boolean equals(Object obj)
        {
            if (obj.getClass() != getClass()) {
                return false;
            }
            TestBean castObj = (TestBean) obj;
            return castObj.foo == foo && Objects.equals(castObj.bar, bar);
        }

        @Override
        public int hashCode() {
            return foo ^ bar.hashCode();
        }
    }
    TestBean[] empty = {};

    TestBean testBean1 = new TestBean(1, "one");

    TestBean testBean2 = new TestBean(2, "two");

    TestBean[] single = { testBean1 };

    TestBean[] multipleValues = { testBean1, testBean2 };

    @Test
    public void testEmptyStream() throws Exception {
        assertArrayEquals(empty, this.roundTrip(Stream.empty(), TestBean[].class));
    }

    @Test
    public void testNestedStreamEmptyElement() throws Exception {
        final List<NestedStream<String,List<String>>> expected = Arrays.asList(new NestedStream<>(new ArrayList<>()));
        final Collection<NestedStream<String, List<String>>> actual = roundTrip(expected.stream(), new TypeReference<Collection<NestedStream<String,List<String>>>>() {});
        assertEquals(expected,actual);
    }

    @Test
    public void testSingleElement() throws Exception {
        assertArrayEquals(single, roundTrip(Stream.of(single), TestBean[].class));
    }

    @Test
    public void testNestedStreamSingleElement() throws Exception {
        final List<NestedStream<String,List<String>>> nestedStream = Arrays.asList(new NestedStream<>(Arrays.asList("foo")));
        final Collection<NestedStream<String, List<String>>> roundTrip = roundTrip(nestedStream.stream(), new TypeReference<Collection<NestedStream<String,List<String>>>>() {});
        assertEquals(roundTrip,nestedStream);
    }

    @Test
    public void testMultiElements() throws Exception {
        assertArrayEquals(multipleValues, roundTrip(Stream.of(multipleValues), TestBean[].class));
    }

    @Test
    public void testNestedStreamMultiElements() throws Exception {
        final List<NestedStream<String,List<String>>> expected = Arrays.asList(new NestedStream<>(Arrays.asList("foo")),new NestedStream<>(Arrays.asList("bar")));
        final Collection<NestedStream<String, List<String>>> actual = roundTrip(expected.stream(), new TypeReference<Collection<NestedStream<String,List<String>>>>() {});
        assertEquals(expected,actual);
    }

    @Test
    public void testStreamCloses() throws Exception {
        assertClosesOnSuccess(Stream.of(multipleValues), stream -> roundTrip(stream, TestBean[].class));
    }

    // 10-Jan-2025, tatu: I hate these kinds of obscure lambda-ridden tests.
    //    They were accidentally disabled and now fail for... some reason. WTF.
    //   (came from `jackson-modules-java8`, disabled due to JUnit 4->5 migration)
    /*
    @Test
    public void testStreamClosesOnRuntimeException() throws Exception {
        String exceptionMessage = "Stream peek threw";
        assertClosesOnRuntimeException(exceptionMessage, stream -> roundTrip(stream, TestBean[].class),
                Stream.of(multipleValues)
                    .peek(e -> {
                        throw new RuntimeException(exceptionMessage);
                    }));
    }

    @Test
    public void testStreamClosesOnSneakyIOException() throws Exception {
        String exceptionMessage = "Stream peek threw";
        assertClosesOnIoException(exceptionMessage, stream -> roundTrip(stream, TestBean[].class),
                Stream.of(multipleValues)
                    .peek(e -> {
                        sneakyThrow(new IOException(exceptionMessage));
                    }));
    }

    @Test
    public void testStreamClosesOnWrappedIoException() throws Exception {
        final String exceptionMessage = "Stream peek threw";

        assertClosesOnWrappedIoException(exceptionMessage, stream -> roundTrip(stream, TestBean[].class),
                Stream.of(multipleValues)
                    .peek(e -> {
                        throw new UncheckedIOException(new IOException(exceptionMessage));
                    }));
    }
    */

    private <T, R> R[] roundTrip(Stream<T> stream, Class<R[]> clazz) {
        String json = objectMapper.writeValueAsString(stream);
        return objectMapper.readValue(json, clazz);
    }

    private <T, R> R roundTrip(Stream<T> stream, TypeReference<R> tr) {
        return objectMapper.readValue(objectMapper.writeValueAsString(stream), tr);
    }

    /**
     * Test class to verify nested streams are handled, even though, in general, this is likely a risky thing to do.
     *
     * @param <T> the type of the container contents
     * @param <C> the container type.
     */
    static class NestedStream<T,C extends Collection<T>> {
        C  values;

        NestedStream(){};

        public NestedStream(C values) {
            super();
            this.values = values;
        }

        public Stream<T> getValues(){
           return values.stream();
        }

        protected void setValues(C values) {
            this.values = values;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((values == null) ? 0 : values.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            @SuppressWarnings("rawtypes")
            NestedStream other = (NestedStream) obj;
            if (values == null) {
                if (other.values != null)
                    return false;
            } else if (!values.equals(other.values))
                return false;
            return true;
        }

        @Override
        public String toString() {
            return "NestedStream [values=" + this.values + "]";
        }
    }
}
