package tools.jackson.databind.jsontype.deftyping;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.*;

import tools.jackson.databind.DefaultTyping;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.testutil.DatabindTestUtil;
import tools.jackson.databind.testutil.NoCheckSubTypeValidator;

import static org.junit.jupiter.api.Assertions.*;

public class TestDefaultWithCreators
    extends DatabindTestUtil
{
    static abstract class Job
    {
        public long id;
    }

    static class UrlJob extends Job
    {
        private final String url;
        private final int count;

        @JsonCreator
        public UrlJob(@JsonProperty("id") long id, @JsonProperty("url") String url,
                @JsonProperty("count") int count)
        {
            this.id = id;
            this.url = url;
            this.count = count;
        }

        public String getUrl() { return url; }
        public int getCount() { return count; }
    }

    // [databind#1385]
    static class Bean1385Wrapper
    {
        public Object value;

        protected Bean1385Wrapper() { }
        public Bean1385Wrapper(Object v) { value = v; }
    }

    static class Bean1385
    {
        byte[] raw;

        @JsonCreator(mode=JsonCreator.Mode.DELEGATING)
        public Bean1385(byte[] raw) {
            this.raw = raw.clone();
        }

        @JsonValue
        public byte[] getBytes() {
            return raw;
        }
    }

    /*
    /**********************************************************
    /* Unit tests
    /**********************************************************
     */

    @Test
    public void testWithCreators() throws Exception
    {
        ObjectMapper mapper = jsonMapperBuilder()
                .activateDefaultTyping(NoCheckSubTypeValidator.instance,
                        DefaultTyping.NON_FINAL)
                .build();
        UrlJob input = new UrlJob(123L, "http://foo", 3);
        String json = mapper.writeValueAsString(input);
        assertNotNull(json);
        Job output = mapper.readValue(json, Job.class);
        assertNotNull(output);
        assertSame(UrlJob.class, output.getClass());
        UrlJob o2 = (UrlJob) output;
        assertEquals(123L, o2.id);
        assertEquals("http://foo", o2.getUrl());
        assertEquals(3, o2.getCount());
    }

    // [databind#1385]
    @Test
    public void testWithCreatorAndJsonValue() throws Exception
    {
        final byte[] BYTES = new byte[] { 1, 2, 3, 4, 5 };
        ObjectMapper mapper = jsonMapperBuilder()
                .activateDefaultTyping(NoCheckSubTypeValidator.instance)
                .build();
        String json = mapper.writeValueAsString(new Bean1385Wrapper(
                new Bean1385(BYTES)
        ));
        Bean1385Wrapper result = mapper.readValue(json, Bean1385Wrapper.class);
        assertNotNull(result);
        assertNotNull(result.value);
        assertEquals(Bean1385.class, result.value.getClass());
        Bean1385 b = (Bean1385) result.value;
        assertArrayEquals(BYTES, b.raw);
    }
 }
