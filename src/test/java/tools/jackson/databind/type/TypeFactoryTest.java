package tools.jackson.databind.type;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.*;
import tools.jackson.databind.testutil.DatabindTestUtil;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Simple tests to verify that the {@link TypeFactory} constructs
 * type information as expected.
 */
public class TypeFactoryTest extends DatabindTestUtil
{
    /*
    /**********************************************************
    /* Helper types
    /**********************************************************
     */

    enum EnumForCanonical { YES, NO; }

    static class SingleArgGeneric<X> { }

    abstract static class MyMap extends IntermediateMap<String,Long> { }
    abstract static class IntermediateMap<K,V> implements Map<K,V> { }

    abstract static class MyList extends IntermediateList<Long> { }
    abstract static class IntermediateList<E> implements List<E> { }

    @SuppressWarnings("serial")
    static class GenericList<T> extends ArrayList<T> { }

    interface MapInterface extends Cloneable, IntermediateInterfaceMap<String> { }
    interface IntermediateInterfaceMap<FOO> extends Map<FOO, Integer> { }

    @SuppressWarnings("serial")
    static class MyStringIntMap extends MyStringXMap<Integer> { }
    @SuppressWarnings("serial")
    static class MyStringXMap<V> extends HashMap<String,V> { }

    // And one more, now with obfuscated type names; essentially it's just Map<Int,Long>
    static abstract class IntLongMap extends XLongMap<Integer> { }
    // trick here is that V now refers to key type, not value type
    static abstract class XLongMap<V> extends XXMap<V,Long> { }
    static abstract class XXMap<K,V> implements Map<K,V> { }

    static class SneakyBean {
        public IntLongMap intMap;
        public MyList longList;
    }

    static class SneakyBean2 {
        // self-reference; should be resolved as "Comparable<Object>"
        public <T extends Comparable<T>> T getFoobar() { return null; }
    }

    @SuppressWarnings("serial")
    public static class LongValuedMap<K> extends HashMap<K, Long> { }

    static class StringLongMapBean {
        public LongValuedMap<String> value;
    }

    static class StringListBean {
        public GenericList<String> value;
    }

    static class CollectionLike<E> { }
    static class MapLike<K,V> { }

    static class Wrapper1297<T> {
        public T content;
    }

    private final TypeFactory TF = defaultTypeFactory();

    /*
    /**********************************************************
    /* Unit tests
    /**********************************************************
     */

    @Test
    public void testSimpleTypes()
    {
        Class<?>[] classes = new Class<?>[] {
            boolean.class, byte.class, char.class,
                short.class, int.class, long.class,
                float.class, double.class,

            Boolean.class, Byte.class, Character.class,
                Short.class, Integer.class, Long.class,
                Float.class, Double.class,

                String.class,
                Object.class,

                Calendar.class,
                Date.class,
        };

        for (Class<?> clz : classes) {
            assertSame(clz, TF.constructType(clz).getRawClass());
            assertSame(clz, TF.constructType(clz).getRawClass());
        }
    }

    @Test
    public void testArrays()
    {
        Class<?>[] classes = new Class<?>[] {
            boolean[].class, byte[].class, char[].class,
                short[].class, int[].class, long[].class,
                float[].class, double[].class,

                String[].class, Object[].class,
                Calendar[].class,
        };

        for (Class<?> clz : classes) {
            assertSame(clz, TF.constructType(clz).getRawClass());
            Class<?> elemType = clz.getComponentType();
            assertSame(clz, TF.constructArrayType(elemType).getRawClass());
        }
    }

    // [databind#810]: Fake Map type for Properties as <String,String>
    @Test
    public void testProperties()
    {
        JavaType t = TF.constructType(Properties.class);
        assertEquals(MapType.class, t.getClass());
        assertSame(Properties.class, t.getRawClass());

        MapType mt = (MapType) t;

        // so far so good. But how about parameterization?
        assertSame(String.class, mt.getKeyType().getRawClass());
        assertSame(String.class, mt.getContentType().getRawClass());
    }

    // note: changed for [databind#3950]
    @Test
    public void testIterator()
    {
        JavaType t = TF.constructType(new TypeReference<Iterator<String>>() { });
        assertEquals(IterationType.class, t.getClass());
        assertTrue(t.isIterationType());
        assertSame(Iterator.class, t.getRawClass());
        assertEquals(1, t.containedTypeCount());
        assertEquals(TF.constructType(String.class), t.containedType(0));
        assertNull(t.containedType(1));
    }

    /**
     * Test for verifying that parametric types can be constructed
     * programmatically
     */
    @Test
    public void testParametricTypes()
    {
        // first, simple class based
        JavaType t = TF.constructParametricType(ArrayList.class, String.class); // ArrayList<String>
        assertEquals(CollectionType.class, t.getClass());
        JavaType strC = TF.constructType(String.class);
        assertEquals(1, t.containedTypeCount());
        assertEquals(strC, t.containedType(0));
        assertNull(t.containedType(1));

        // Then using JavaType
        JavaType t2 = TF.constructParametricType(Map.class, strC, t); // Map<String,ArrayList<String>>
        // should actually produce a MapType
        assertEquals(MapType.class, t2.getClass());
        assertEquals(2, t2.containedTypeCount());
        assertEquals(strC, t2.containedType(0));
        assertEquals(t, t2.containedType(1));
        assertNull(t2.containedType(2));

        // Then using TypeBindings
        JavaType t3 = TF.constructParametricType(HashSet.class, t.getBindings()); // HashSet<String>
        assertEquals(CollectionType.class, t3.getClass());
        assertEquals(1, t3.containedTypeCount());
        assertEquals(strC, t3.containedType(0));
        assertNull(t3.containedType(1));

        // Then custom generic type as well
        JavaType custom = TF.constructParametricType(SingleArgGeneric.class, String.class);
        assertEquals(SimpleType.class, custom.getClass());
        assertEquals(1, custom.containedTypeCount());
        assertEquals(strC, custom.containedType(0));
        assertNull(custom.containedType(1));

        // and then custom generic type from TypeBindings
        JavaType custom2 = TF.constructParametricType(SingleArgGeneric.class, t.getBindings());
        assertEquals(SimpleType.class, custom2.getClass());
        assertEquals(1, custom2.containedTypeCount());
        assertEquals(strC, custom2.containedType(0));
        assertNull(custom2.containedType(1));

        // And finally, ensure that we can't create invalid combinations
        try {
            // Maps must take 2 type parameters, not just one
            TF.constructParametricType(Map.class, strC);
        } catch (IllegalArgumentException e) {
            verifyException(e, "Cannot create TypeBindings for class java.util.Map");
        }

        try {
            // Type only accepts one type param
            TF.constructParametricType(SingleArgGeneric.class, strC, strC);
        } catch (IllegalArgumentException e) {
            verifyException(e, "Cannot create TypeBindings for class ");
        }
    }

    /**
     * Test for checking that canonical name handling works ok
     */
    @Test
    public void testCanonicalNames()
    {
        JavaType t = TF.constructType(java.util.Calendar.class);
        String can = t.toCanonical();
        assertEquals("java.util.Calendar", can);
        assertEquals(t, TF.constructFromCanonical(can));

        // Generic maps and collections will default to Object.class if type-erased
        t = TF.constructType(java.util.ArrayList.class);
        can = t.toCanonical();
        assertEquals("java.util.ArrayList<java.lang.Object>", can);
        assertEquals(t, TF.constructFromCanonical(can));

        t = TF.constructType(java.util.TreeMap.class);
        can = t.toCanonical();
        assertEquals("java.util.TreeMap<java.lang.Object,java.lang.Object>", can);
        assertEquals(t, TF.constructFromCanonical(can));

        // And then EnumMap (actual use case for us)
        t = TF.constructMapType(EnumMap.class, EnumForCanonical.class, String.class);
        can = t.toCanonical();
        assertEquals("java.util.EnumMap<tools.jackson.databind.type.TypeFactoryTest$EnumForCanonical,java.lang.String>",
                can);
        assertEquals(t, TF.constructFromCanonical(can));

        // [databind#2109]: also ReferenceTypes
        t = TF.constructType(new TypeReference<AtomicReference<Long>>() { });
        assertTrue(t.isReferenceType());
        can = t.toCanonical();
        assertEquals("java.util.concurrent.atomic.AtomicReference<java.lang.Long>",
                can);
        assertEquals(t, TF.constructFromCanonical(can));

        // [databind#1941]: allow "raw" types too
        t = TF.constructFromCanonical("java.util.List");
        assertEquals(List.class, t.getRawClass());
        assertEquals(CollectionType.class, t.getClass());
        // 01-Mar-2018, tatu: not 100% should we expect type parameters here...
        //    But currently we do NOT get any
        /*
        assertEquals(1, t.containedTypeCount());
        assertEquals(Object.class, t.containedType(0).getRawClass());
        */
        assertEquals(Object.class, t.getContentType().getRawClass());
        can = t.toCanonical();
        assertEquals("java.util.List<java.lang.Object>", can);
        assertEquals(t, TF.constructFromCanonical(can));
    }

    // [databind#1768]
    @SuppressWarnings("serial")
    @Test
    public void testCanonicalWithSpaces()
    {
        Object objects = new TreeMap<Object, Object>() { }; // to get subtype
        String reflectTypeName = objects.getClass().getGenericSuperclass().toString();
        JavaType t1 = TF.constructType(objects.getClass().getGenericSuperclass());
        // This will throw an Exception if you don't remove all white spaces from the String.
        JavaType t2 = TF.constructFromCanonical(reflectTypeName);
        assertNotNull(t2);
        assertEquals(t2, t1);
    }

    // [databind#4011]
    @Test
    public void testMalicousCanonical()
    {
        // First: too deep nesting
        final int NESTING = TypeParser.MAX_TYPE_NESTING + 100;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < NESTING; ++i) {
            sb.append("java.util.List<");
        }
        sb.append("java.lang.String");
        for (int i = 0; i < NESTING; ++i) {
            sb.append('>');
        }

        final String deepCanonical = sb.toString();
        Exception e = assertThrows(IllegalArgumentException.class,
                   () -> TF.constructFromCanonical(deepCanonical));
        verifyException(e, "too deeply nested");

        // And second, too long in general
        final int MAX_LEN = TypeParser.MAX_TYPE_LENGTH + 100;
        sb = new StringBuilder().append("java.util.List<");
        while (sb.length() < MAX_LEN) {
            sb.append("java.lang.String,");
        }
        sb.append("java.lang.Integer>");
        final String longCanonical = sb.toString();
        e = assertThrows(IllegalArgumentException.class,
                () -> TF.constructFromCanonical(longCanonical));
         verifyException(e, "too long");
    }

    /*
    /**********************************************************
    /* Unit tests: collection type parameter resolution
    /**********************************************************
     */

    @Test
    public void testCollections()
    {
        // Ok, first: let's test what happens when we pass 'raw' Collection:
        JavaType t = TF.constructType(ArrayList.class);
        assertEquals(CollectionType.class, t.getClass());
        assertSame(ArrayList.class, t.getRawClass());
        assertSame(Object.class, ((CollectionType) t).getContentType().getRawClass());

        // Also check equality (so caching by JavaType will work)
        assertEqualsAndHash(t, TF.constructType(ArrayList.class));

        // And then the proper way
        t = TF.constructType(new TypeReference<ArrayList<String>>() { });
        assertEquals(CollectionType.class, t.getClass());
        assertSame(ArrayList.class, t.getRawClass());

        JavaType elemType = ((CollectionType) t).getContentType();
        assertNotNull(elemType);
        assertSame(SimpleType.class, elemType.getClass());
        assertSame(String.class, elemType.getRawClass());

        assertEqualsAndHash(t, TF.constructType(new TypeReference<ArrayList<String>>() { }));

        // And alternate method too
        t = TF.constructCollectionType(ArrayList.class, String.class);
        assertEquals(CollectionType.class, t.getClass());
        assertSame(String.class, ((CollectionType) t).getContentType().getRawClass());

        assertEqualsAndHash(t, TF.constructCollectionType(ArrayList.class, String.class));
    }

    @Test
    public void testCollectionTypesRefined()
    {
        TypeFactory tf = newTypeFactory();
        JavaType type = tf.constructType(new TypeReference<List<Long>>() { });
        assertEquals(List.class, type.getRawClass());
        assertEquals(Long.class, type.getContentType().getRawClass());
        // No super-class, since it's an interface:
        assertNull(type.getSuperClass());
        // Ensure identity-eq works:
        assertEqualsAndHash(type, tf.constructType(new TypeReference<List<Long>>() { }));

        // But then refine to reflect sub-classing
        JavaType subtype = tf.constructSpecializedType(type, ArrayList.class);
        assertEquals(ArrayList.class, subtype.getRawClass());
        assertEquals(Long.class, subtype.getContentType().getRawClass());

        assertEqualsAndHash(subtype, tf.constructSpecializedType(type, ArrayList.class));

        // but with refinement, should have non-null super class
        JavaType superType = subtype.getSuperClass();
        assertNotNull(superType);
        assertEquals(AbstractList.class, superType.getRawClass());
    }

    // for [databind#3876]
    @SuppressWarnings("rawtypes")
    @Test
    public void testCollectionsHashCode()
    {
        TypeFactory tf = newTypeFactory();
        JavaType listOfCollection = tf.constructType(new TypeReference<List<Collection>>() { });
        JavaType collectionOfList = tf.constructType(new TypeReference<Collection<List>>() { });
        assertNotEquals(listOfCollection, collectionOfList);
        assertNotEquals(listOfCollection.hashCode(), collectionOfList.hashCode());
    }

    /*
    /**********************************************************
    /* Unit tests: map type parameter resolution
    /**********************************************************
     */

    @Test
    public void testMaps()
    {
        TypeFactory tf = newTypeFactory();

        // Ok, first: let's test what happens when we pass 'raw' Map:
        JavaType t = tf.constructType(HashMap.class);
        assertEquals(MapType.class, t.getClass());
        assertSame(HashMap.class, t.getRawClass());
        assertEqualsAndHash(t, tf.constructType(HashMap.class));

        // Then explicit construction
        t = tf.constructMapType(TreeMap.class, String.class, Integer.class);
        assertEquals(MapType.class, t.getClass());
        assertSame(String.class, ((MapType) t).getKeyType().getRawClass());
        assertSame(Integer.class, ((MapType) t).getContentType().getRawClass());
        assertEqualsAndHash(t, tf.constructMapType(TreeMap.class, String.class, Integer.class));

        // And then with TypeReference
        t = tf.constructType(new TypeReference<HashMap<String,Integer>>() { });
        assertEquals(MapType.class, t.getClass());
        assertSame(HashMap.class, t.getRawClass());
        MapType mt = (MapType) t;
        assertEquals(tf.constructType(String.class), mt.getKeyType());
        assertEquals(tf.constructType(Integer.class), mt.getContentType());
        assertEqualsAndHash(t, tf.constructType(new TypeReference<HashMap<String,Integer>>() { }));

        t = tf.constructType(new TypeReference<LongValuedMap<Boolean>>() { });
        assertEquals(MapType.class, t.getClass());
        assertSame(LongValuedMap.class, t.getRawClass());
        mt = (MapType) t;
        assertEquals(tf.constructType(Boolean.class), mt.getKeyType());
        assertEquals(tf.constructType(Long.class), mt.getContentType());
        assertEqualsAndHash(t, tf.constructType(new TypeReference<LongValuedMap<Boolean>>() { }));

        JavaType type = tf.constructType(new TypeReference<Map<String,Boolean>>() { });
        MapType mapType = (MapType) type;
        assertEquals(tf.constructType(String.class), mapType.getKeyType());
        assertEquals(tf.constructType(Boolean.class), mapType.getContentType());
        assertEqualsAndHash(type, tf.constructType(new TypeReference<Map<String,Boolean>>() { }));
    }
    
    // for [databind#3876]
    @Test
    public void testMapsHashCode()
    {
        TypeFactory tf = newTypeFactory();
        JavaType mapStringInt = tf.constructType(new TypeReference<Map<String,Integer>>() {});
        JavaType mapIntString = tf.constructType(new TypeReference<Map<Integer,String>>() {});
        assertNotEquals(mapStringInt, mapIntString);
        assertNotEquals(
                mapStringInt.hashCode(),
                mapIntString.hashCode(),
                "hashCode should depend on parameter order");

        JavaType mapStringString = tf.constructType(new TypeReference<Map<String,String>>() {});
        JavaType mapIntInt = tf.constructType(new TypeReference<Map<Integer,Integer>>() {});
        assertNotEquals(mapStringString, mapIntInt);
        assertNotEquals(mapStringString.hashCode(), mapIntInt.hashCode());
    }

    // since 2.7
    @Test
    public void testMapTypesRefined()
    {
        TypeFactory tf = newTypeFactory();
        JavaType type = tf.constructType(new TypeReference<Map<String,List<Integer>>>() { });
        assertEquals(MapType.class, type.getClass());
        MapType mapType = (MapType) type;
        assertEquals(Map.class, mapType.getRawClass());
        assertEquals(String.class, mapType.getKeyType().getRawClass());
        assertEquals(List.class, mapType.getContentType().getRawClass());
        assertEquals(Integer.class, mapType.getContentType().getContentType().getRawClass());
        // No super-class, since it's an interface:
        assertNull(type.getSuperClass());
        assertEqualsAndHash(type, tf.constructType(new TypeReference<Map<String,List<Integer>>>() { }));

        // But then refine to reflect sub-classing
        JavaType subtype = tf.constructSpecializedType(type, LinkedHashMap.class);
        assertEquals(LinkedHashMap.class, subtype.getRawClass());
        assertEquals(String.class, subtype.getKeyType().getRawClass());
        assertEquals(List.class, subtype.getContentType().getRawClass());
        assertEquals(Integer.class, subtype.getContentType().getContentType().getRawClass());
        assertEqualsAndHash(subtype, tf.constructSpecializedType(type, LinkedHashMap.class));

        // but with refinement, should have non-null super class

        JavaType superType = subtype.getSuperClass();
        assertNotNull(superType);
        assertEquals(HashMap.class, superType.getRawClass());
        // which also should have proper typing
        assertEquals(String.class, superType.getKeyType().getRawClass());
        assertEquals(List.class, superType.getContentType().getRawClass());
        assertEquals(Integer.class, superType.getContentType().getContentType().getRawClass());
    }

    @Test
    public void testTypeGeneralization()
    {
        TypeFactory tf = newTypeFactory();
        MapType t = tf.constructMapType(HashMap.class, String.class, Long.class);
        JavaType superT = tf.constructGeneralizedType(t, Map.class);
        assertEquals(String.class, superT.getKeyType().getRawClass());
        assertEquals(Long.class, superT.getContentType().getRawClass());

        assertSame(t, tf.constructGeneralizedType(t, HashMap.class));

        // plus check there is super/sub relationship
        try {
            tf.constructGeneralizedType(t, TreeMap.class);
            fail("Should not pass");
        } catch (IllegalArgumentException e) {
            verifyException(e, "not a super-type of");
        }
    }

    @Test
    public void testMapTypesRaw()
    {
        JavaType type = TF.constructType(HashMap.class);
        assertEquals(MapType.class, type.getClass());
        MapType mapType = (MapType) type;
        assertEquals(TF.constructType(Object.class), mapType.getKeyType());
        assertEquals(TF.constructType(Object.class), mapType.getContentType());
    }

    @Test
    public void testMapTypesAdvanced()
    {
        JavaType type = TF.constructType(MyMap.class);
        assertEquals(MapType.class, type.getClass());
        MapType mapType = (MapType) type;
        assertEquals(TF.constructType(String.class), mapType.getKeyType());
        assertEquals(TF.constructType(Long.class), mapType.getContentType());

        type = TF.constructType(MapInterface.class);
        mapType = (MapType) type;

        assertEquals(TF.constructType(String.class), mapType.getKeyType());
        assertEquals(TF.constructType(Integer.class), mapType.getContentType());

        type = TF.constructType(MyStringIntMap.class);
        mapType = (MapType) type;
        assertEquals(TF.constructType(String.class), mapType.getKeyType());
        assertEquals(TF.constructType(Integer.class), mapType.getContentType());
    }

    /**
     * Specific test to verify that complicate name mangling schemes
     * do not fool type resolver
     */
    @Test
    public void testMapTypesSneaky()
    {
        JavaType type = TF.constructType(IntLongMap.class);
        assertEquals(MapType.class, type.getClass());
        MapType mapType = (MapType) type;
        assertEquals(TF.constructType(Integer.class), mapType.getKeyType());
        assertEquals(TF.constructType(Long.class), mapType.getContentType());
    }

    /**
     * Plus sneaky types may be found via introspection as well.
     */
    @Test
    public void testSneakyFieldTypes() throws Exception
    {
        Field field = SneakyBean.class.getDeclaredField("intMap");
        JavaType type = TF.constructType(field.getGenericType());
        assertEquals(MapType.class, type.getClass());
        MapType mapType = (MapType) type;
        assertEquals(TF.constructType(Integer.class), mapType.getKeyType());
        assertEquals(TF.constructType(Long.class), mapType.getContentType());

        field = SneakyBean.class.getDeclaredField("longList");
        type = TF.constructType(field.getGenericType());
        assertTrue(type instanceof CollectionType);
        CollectionType collectionType = (CollectionType) type;
        assertEquals(TF.constructType(Long.class), collectionType.getContentType());
    }

    /**
     * Looks like type handling actually differs for properties, too.
     */
    @Test
    public void testSneakyBeanProperties() throws Exception
    {
        ObjectMapper mapper = new ObjectMapper();
        StringLongMapBean bean = mapper.readValue("{\"value\":{\"a\":123}}", StringLongMapBean.class);
        assertNotNull(bean);
        Map<String,Long> map = bean.value;
        assertEquals(1, map.size());
        assertEquals(Long.valueOf(123), map.get("a"));

        StringListBean bean2 = mapper.readValue("{\"value\":[\"...\"]}", StringListBean.class);
        assertNotNull(bean2);
        List<String> list = bean2.value;
        assertSame(GenericList.class, list.getClass());
        assertEquals(1, list.size());
        assertEquals("...", list.get(0));
    }

    @Test
    public void testSneakySelfRefs() throws Exception
    {
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(new SneakyBean2());
        assertEquals("{\"foobar\":null}", json);
    }

    /*
    /**********************************************************
    /* Unit tests: handling of specific JDK types
    /**********************************************************
     */

    @Test
    public void testAtomicArrayRefParameters()
    {
        JavaType type = TF.constructType(new TypeReference<AtomicReference<long[]>>() { });
        JavaType[] params = TF.findTypeParameters(type, AtomicReference.class);
        assertNotNull(params);
        assertEquals(1, params.length);
        assertEquals(TF.constructType(long[].class), params[0]);
    }

    static abstract class StringIntMapEntry implements Map.Entry<String,Integer> { }

    @Test
    public void testMapEntryResolution()
    {
        JavaType t = TF.constructType(StringIntMapEntry.class);
        JavaType mapEntryType = t.findSuperType(Map.Entry.class);
        assertNotNull(mapEntryType);
        assertTrue(mapEntryType.hasGenericTypes());
        assertEquals(2, mapEntryType.containedTypeCount());
        assertEquals(String.class, mapEntryType.containedType(0).getRawClass());
        assertEquals(Integer.class, mapEntryType.containedType(1).getRawClass());
    }

    /*
    /**********************************************************
    /* Unit tests: construction of "raw" types
    /**********************************************************
     */

    @Test
    public void testRawCollections()
    {
        JavaType type = TF.constructRawCollectionType(ArrayList.class);
        assertTrue(type.isContainerType());
        assertEquals(TypeFactory.unknownType(), type.getContentType());
        type = TF.constructRawCollectionLikeType(CollectionLike.class); // must have type vars
        assertTrue(type.isCollectionLikeType());
        assertEquals(TypeFactory.unknownType(), type.getContentType());

        // actually, should also allow "no type vars" case
        type = TF.constructRawCollectionLikeType(String.class);
        assertTrue(type.isCollectionLikeType());
        assertEquals(TypeFactory.unknownType(), type.getContentType());
    }

    @Test
    public void testRawMaps()
    {
        JavaType type = TF.constructRawMapType(HashMap.class);
        assertTrue(type.isContainerType());
        assertEquals(TypeFactory.unknownType(), type.getKeyType());
        assertEquals(TypeFactory.unknownType(), type.getContentType());

        type = TF.constructRawMapLikeType(MapLike.class); // must have type vars
        assertTrue(type.isMapLikeType());
        assertEquals(TypeFactory.unknownType(), type.getKeyType());
        assertEquals(TypeFactory.unknownType(), type.getContentType());

        // actually, should also allow "no type vars" case
        type = TF.constructRawMapLikeType(String.class);
        assertTrue(type.isMapLikeType());
        assertEquals(TypeFactory.unknownType(), type.getKeyType());
        assertEquals(TypeFactory.unknownType(), type.getContentType());
    }

    /*
    /**********************************************************
    /* Unit tests: other
    /**********************************************************
     */

    @Test
    public void testMoreSpecificType()
    {
        JavaType t1 = TF.constructCollectionType(Collection.class, Object.class);
        JavaType t2 = TF.constructCollectionType(List.class, Object.class);
        assertSame(t2, TF.moreSpecificType(t1, t2));
        assertSame(t2, TF.moreSpecificType(t2, t1));

        t1 = TF.constructType(Double.class);
        t2 = TF.constructType(Number.class);
        assertSame(t1, TF.moreSpecificType(t1, t2));
        assertSame(t1, TF.moreSpecificType(t2, t1));

        // and then unrelated, return first
        t1 = TF.constructType(Double.class);
        t2 = TF.constructType(String.class);
        assertSame(t1, TF.moreSpecificType(t1, t2));
        assertSame(t2, TF.moreSpecificType(t2, t1));
    }

    // [databind#489]
    @Test
    public void testCacheClearing()
    {
        TypeFactory tf = TF.withModifier(null);
        assertEquals(0, tf._typeCache.size());
        tf.constructType(getClass());
        // 19-Oct-2015, tatu: This is pretty fragile but
        assertTrue(tf._typeCache.size() > 0);
        tf.clearCache();
        assertEquals(0, tf._typeCache.size());
    }

    // for [databind#1297]
    @Test
    public void testRawMapType()
    {
        TypeFactory tf = TF.withModifier(null); // to get a new copy

        JavaType type = tf.constructParametricType(Wrapper1297.class, Map.class);
        assertNotNull(type);
        assertEquals(Wrapper1297.class, type.getRawClass());
    }

    // for [databind#3443]
    @Test
    public void testParameterizedClassType() {
        JavaType t = TF.constructType(new TypeReference<Class<? extends CharSequence>>() { });

        assertEquals(SimpleType.class, t.getClass());
        assertEquals(1, t.containedTypeCount());
        assertEquals(CharSequence.class, t.containedType(0).getRawClass());
    }

    // for [databind#3876]
    @Test
    public void testParameterizedSimpleType() {
        JavaType charSequenceClass = TF.constructType(new TypeReference<Class<? extends CharSequence>>() { });
        JavaType numberClass = TF.constructType(new TypeReference<Class<? extends Number>>() { });

        assertEquals(SimpleType.class, charSequenceClass.getClass());
        assertEquals(SimpleType.class, numberClass.getClass());

        assertNotEquals(charSequenceClass, numberClass);
        assertNotEquals(
                charSequenceClass.hashCode(), numberClass.hashCode(),
                "hash values should be distributed");
    }

    private void assertEqualsAndHash(JavaType t1, JavaType t2) {
        assertEquals(t1, t2);
        assertEquals(t2, t1);
        assertEquals(t1.hashCode(), t2.hashCode());
    }
}
