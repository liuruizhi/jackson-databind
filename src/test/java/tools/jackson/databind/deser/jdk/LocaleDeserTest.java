package tools.jackson.databind.deser.jdk;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.Test;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import static tools.jackson.databind.testutil.DatabindTestUtil.newJsonMapper;
import static tools.jackson.databind.testutil.DatabindTestUtil.q;

// Tests for `java.util.Locale`.
// NOTE: warnings are due to JDK 19 deprecating Locale constructors
@SuppressWarnings("deprecation")
public class LocaleDeserTest
{
    private final Locale[] LOCALES = new Locale[]
            {Locale.CANADA, Locale.ROOT, Locale.GERMAN, Locale.CHINESE, Locale.KOREA, Locale.TAIWAN};

    /*
    /**********************************************************************
    /* Test methods, old, from Jackson pre-2.13
    /**********************************************************************
     */

    private final ObjectMapper MAPPER = newJsonMapper();

    @Test
    public void testLocale() throws Exception
    {
        // Simplest, one part
        assertEquals(new Locale("en"),
                MAPPER.readValue(q("en"), Locale.class));
    }

    public void testLocaleTwoPart() throws IOException
    {
        // Simple; language+country
        assertEquals(new Locale("es", "ES"),
                MAPPER.readValue(q("es-ES"), Locale.class));
        assertEquals(new Locale("es", "ES"),
                MAPPER.readValue(q("es_ES"), Locale.class));
        assertEquals(new Locale("en", "US"),
                MAPPER.readValue(q("en-US"), Locale.class));
        assertEquals(new Locale("en", "US"),
                MAPPER.readValue(q("en_US"), Locale.class));

        assertEquals(Locale.CHINA,
                MAPPER.readValue(q("zh-CN"), Locale.class));
        assertEquals(Locale.CHINA,
                MAPPER.readValue(q("zh_CN"), Locale.class));
    }

    public void testLocaleThreePart() throws IOException
    {
        assertEquals(new Locale("FI", "fi", "savo"),
                MAPPER.readValue(q("fi_FI_savo"), Locale.class));
    }

    @Test
    public void testLocaleKeyMap() throws Exception {
        Locale key = Locale.CHINA;

        // .toString() or .toLanguageTag()?
        String JSON = "{ \"" + key.toString() + "\":4}";
        Map<Locale, Object> result = MAPPER.readValue(JSON, new TypeReference<Map<Locale, Object>>() {
        });
        assertNotNull(result);
        assertEquals(1, result.size());
        Object ob = result.keySet().iterator().next();
        assertNotNull(ob);
        assertEquals(Locale.class, ob.getClass());
        assertEquals(key, ob);
    }

    /*
    /**********************************************************************
    /* Test methods, advanced (2.13+) -- [databind#3259]
    /**********************************************************************
     */

    @Test
    public void testLocaleDeserializeNonBCPFormat1() throws Exception
    {
        Locale locale = new Locale("en", "US");
        Locale deSerializedLocale = MAPPER.readValue(MAPPER.writeValueAsString(locale), Locale.class);
        assertBaseValues(locale, deSerializedLocale);

        locale = new Locale("en");
        deSerializedLocale = MAPPER.readValue(MAPPER.writeValueAsString(locale), Locale.class);
        assertBaseValues(locale, deSerializedLocale);

        locale = new Locale("en", "US", "VARIANT");
        deSerializedLocale = MAPPER.readValue(MAPPER.writeValueAsString(locale), Locale.class);
        assertBaseValues(locale, deSerializedLocale);
    }

    @Test
    public void testLocaleDeserializeNonBCPFormat2() throws Exception
    {
        Locale locale, deSerializedLocale;

        // 10-Sep-2021, tatu: Will get serialized as "en_VARIANT" which won't roundtrip
        //     ... same for others
        locale = new Locale("en", "", "VARIANT");
        deSerializedLocale = MAPPER.readValue(MAPPER.writeValueAsString(locale),
                Locale.class);
        assertBaseValues(locale, deSerializedLocale);

        // But "unknown" language handling does work
        locale = new Locale("", "US", "VARIANT");
        deSerializedLocale = MAPPER.readValue(MAPPER.writeValueAsString(locale), Locale.class);
        assertBaseValues(locale, deSerializedLocale);

        locale = new Locale("", "US", "");
        deSerializedLocale = MAPPER.readValue(MAPPER.writeValueAsString(locale), Locale.class);
        assertBaseValues(locale, deSerializedLocale);
    }

    @Test
    public void testLocaleDeserializeWithScript() throws Exception {
        Locale locale = new Locale.Builder().setLanguage("en").setRegion("GB").setVariant("VARIANT")
                .setScript("Latn").build();
        Locale deSerializedLocale = MAPPER.readValue(MAPPER.writeValueAsString(locale), Locale.class);
        assertLocaleWithScript(locale, deSerializedLocale);

        locale = new Locale.Builder().setRegion("IN").setScript("Latn").build();
        deSerializedLocale = MAPPER.readValue(MAPPER.writeValueAsString(locale), Locale.class);
        assertLocaleWithScript(locale, deSerializedLocale);

        locale = new Locale.Builder().setRegion("CA").setVariant("VARIANT").setScript("Latn").build();
        deSerializedLocale = MAPPER.readValue(MAPPER.writeValueAsString(locale), Locale.class);
        assertLocaleWithScript(locale, deSerializedLocale);
    }

    // 10-Sep-2021, tatu: Does not round-trip correctly, for whatever reason:
    @Test
    public void testLocaleDeserializeWithScript2() throws Exception
    {
        Locale locale, deSerializedLocale;

        locale = new Locale.Builder().setLanguage("en").setScript("Latn").build();
        deSerializedLocale = MAPPER.readValue(MAPPER.writeValueAsString(locale), Locale.class);
        assertLocaleWithScript(locale, deSerializedLocale);

        locale = new Locale.Builder().setLanguage("fr").setRegion("CA").setScript("Latn").build();
        deSerializedLocale = MAPPER.readValue(MAPPER.writeValueAsString(locale), Locale.class);
        assertLocaleWithScript(locale, deSerializedLocale);

        locale = new Locale.Builder().setLanguage("it").setVariant("VARIANT").setScript("Latn").build();
        deSerializedLocale = MAPPER.readValue(MAPPER.writeValueAsString(locale), Locale.class);
        assertLocaleWithScript(locale, deSerializedLocale);
    }

    @Test
    public void testLocaleDeserializeWithExtension() throws Exception {
        Locale locale = new Locale.Builder().setLanguage("en").setRegion("GB").setVariant("VARIANT")
                .setExtension('x', "dummy").build();
        String json = MAPPER.writeValueAsString(locale);
        Locale deSerializedLocale = MAPPER.readValue(json, Locale.class);
        assertLocaleWithExtension(locale, deSerializedLocale);

        locale = new Locale.Builder().setRegion("IN").setExtension('x', "dummy").build();
        deSerializedLocale = MAPPER.readValue(MAPPER.writeValueAsString(locale), Locale.class);
        assertLocaleWithScript(locale, deSerializedLocale);

        locale = new Locale.Builder().setLanguage("fr").setRegion("CA").setExtension('x', "dummy").build();
        deSerializedLocale = MAPPER.readValue(MAPPER.writeValueAsString(locale), Locale.class);
        assertLocaleWithScript(locale, deSerializedLocale);

        locale = new Locale.Builder().setRegion("CA").setVariant("VARIANT").setExtension('x', "dummy").build();
        deSerializedLocale = MAPPER.readValue(MAPPER.writeValueAsString(locale), Locale.class);
        assertLocaleWithScript(locale, deSerializedLocale);

        locale = new Locale.Builder().setLanguage("it").setVariant("VARIANT").setExtension('x', "dummy").build();
        deSerializedLocale = MAPPER.readValue(MAPPER.writeValueAsString(locale), Locale.class);
        assertLocaleWithScript(locale, deSerializedLocale);
    }

    @Test
    public void testLocaleDeserializeWithExtension2() throws Exception
    {
        Locale locale = new Locale.Builder().setLanguage("en").setExtension('x', "dummy").build();
        String json = MAPPER.writeValueAsString(locale);
        Locale deSerializedLocale = MAPPER.readValue(json, Locale.class);
        assertLocaleWithScript(locale, deSerializedLocale);
    }

    @Test
    public void testLocaleDeserializeWithScriptAndExtension() throws Exception {
        Locale locale = new Locale.Builder().setLanguage("en").setRegion("GB").setVariant("VARIANT")
                .setExtension('x', "dummy").setScript("latn").build();
        Locale deSerializedLocale = MAPPER.readValue(MAPPER.writeValueAsString(locale), Locale.class);
        assertLocale(locale, deSerializedLocale);

        locale = new Locale.Builder().setLanguage("en").setExtension('x', "dummy").setScript("latn").build();
        deSerializedLocale = MAPPER.readValue(MAPPER.writeValueAsString(locale), Locale.class);
        assertLocale(locale, deSerializedLocale);

        locale = new Locale.Builder().setRegion("IN").setExtension('x', "dummy").setScript("latn").build();
        deSerializedLocale = MAPPER.readValue(MAPPER.writeValueAsString(locale), Locale.class);
        assertLocale(locale, deSerializedLocale);

        locale = new Locale.Builder().setLanguage("fr").setRegion("CA")
                .setExtension('x', "dummy").setScript("latn").build();
        deSerializedLocale = MAPPER.readValue(MAPPER.writeValueAsString(locale), Locale.class);
        assertLocale(locale, deSerializedLocale);

        locale = new Locale.Builder().setRegion("CA").setVariant("VARIANT")
                .setExtension('x', "dummy").setScript("latn").build();
        deSerializedLocale = MAPPER.readValue(MAPPER.writeValueAsString(locale), Locale.class);
        assertLocale(locale, deSerializedLocale);

        locale = new Locale.Builder().setLanguage("it").setVariant("VARIANT")
                .setExtension('x', "dummy").setScript("latn").build();
        deSerializedLocale = MAPPER.readValue(MAPPER.writeValueAsString(locale), Locale.class);
        assertLocale(locale, deSerializedLocale);
    }

    @Test
    public void testLocaleDeserializeWithLanguageTag() throws Exception {
        Locale locale = Locale.forLanguageTag("en-US-x-debug");
        Locale deSerializedLocale = MAPPER.readValue(MAPPER.writeValueAsString(locale), Locale.class);
        assertLocale(locale, deSerializedLocale);

        locale = Locale.forLanguageTag("en-US-x-lvariant-POSIX");
        deSerializedLocale = MAPPER.readValue(MAPPER.writeValueAsString(locale), Locale.class);
        assertLocale(locale, deSerializedLocale);

        locale = Locale.forLanguageTag("de-POSIX-x-URP-lvariant-AbcDef");
        deSerializedLocale = MAPPER.readValue(MAPPER.writeValueAsString(locale), Locale.class);
        assertBaseValues(locale, deSerializedLocale);

        locale = Locale.forLanguageTag("ar-aao");
        deSerializedLocale = MAPPER.readValue(MAPPER.writeValueAsString(locale), Locale.class);
        assertLocale(locale, deSerializedLocale);

        locale = Locale.forLanguageTag("en-abc-def-us");
        deSerializedLocale = MAPPER.readValue(MAPPER.writeValueAsString(locale), Locale.class);
        assertLocale(locale, deSerializedLocale);
    }

    @Test
    public void testIllFormedVariant() throws Exception {
        Locale locale = Locale.forLanguageTag("de-POSIX-x-URP-lvariant-Abc-Def");
        Locale deSerializedLocale = MAPPER.readValue(MAPPER.writeValueAsString(locale), Locale.class);
        assertBaseValues(locale, deSerializedLocale);
    }

    @Test
    public void testLocaleDeserializeWithLocaleConstants() throws Exception {
        for (Locale locale: LOCALES) {
            Locale deSerializedLocale = MAPPER.readValue(MAPPER.writeValueAsString(locale), Locale.class);
            assertLocale(locale, deSerializedLocale);
        }
    }

    @Test
    public void testSpecialCases() throws Exception {
        Locale locale = new Locale("ja", "JP", "JP");
        Locale deSerializedLocale = MAPPER.readValue(MAPPER.writeValueAsString(locale), Locale.class);
        assertLocale(locale, deSerializedLocale);

        locale = new Locale("th", "TH", "TH");
        deSerializedLocale = MAPPER.readValue(MAPPER.writeValueAsString(locale), Locale.class);
        assertLocale(locale, deSerializedLocale);
    }

    private void assertBaseValues(Locale expected, Locale actual) {
        assertEquals(expected.getLanguage(), actual.getLanguage(), "Language mismatch");
        assertEquals(expected.getCountry(), actual.getCountry(), "Country mismatch");
        assertEquals(expected.getVariant(), actual.getVariant(), "Variant mismatch");
    }

    private void assertLocaleWithScript(Locale expected, Locale actual) {
        assertBaseValues(expected, actual);
        assertEquals(expected.getScript(), actual.getScript(), "Script mismatch");
    }

    private void assertLocaleWithExtension(Locale expected, Locale actual) {
        assertBaseValues(expected, actual);
        assertEquals(expected.getExtension('x'), actual.getExtension('x'), "Extension mismatch");
    }

    private void assertLocale(Locale expected, Locale actual) {
        assertBaseValues(expected, actual);
        assertEquals(expected.getExtension('x'), actual.getExtension('x'), "Extension mismatch");
        assertEquals(expected.getScript(), actual.getScript(), "Script mismatch");
    }

    // https://bugs.chromium.org/p/oss-fuzz/issues/detail?id=47034
    // @since 2.14
    @Test
    public void testLocaleFuzz47034() throws Exception
    {
        Locale loc = MAPPER.readerFor(Locale.class)
                .without(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .readValue(getClass().getResourceAsStream("/fuzz/oss-fuzz-47034.json"));
        assertNotNull(loc);
    }

    // https://bugs.chromium.org/p/oss-fuzz/issues/detail?id=47036
    // @since 2.14
    @Test
    public void testLocaleFuzz47036() throws Exception
    {
        Locale loc = MAPPER.readerFor(Locale.class)
                .without(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .readValue(getClass().getResourceAsStream("/fuzz/oss-fuzz-47036.json"));
        assertNotNull(loc);
    }
}
