package tools.jackson.databind.ser;

import java.lang.annotation.Annotation;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.*;
import tools.jackson.databind.introspect.BeanPropertyDefinition;
import tools.jackson.databind.introspect.ConcreteBeanPropertyBase;
import tools.jackson.databind.jsonFormatVisitors.JsonObjectFormatVisitor;

/**
 * Base class for writers used to output property values (name-value pairs)
 * as key/value pairs via streaming API. This is the most generic abstraction
 * implemented by both POJO and {@link java.util.Map} serializers, and invoked
 * by filtering functionality.
 */
public abstract class PropertyWriter
    extends ConcreteBeanPropertyBase
    implements java.io.Serializable
{
    private static final long serialVersionUID = 1L;

    protected PropertyWriter(PropertyMetadata md) {
        super(md);
    }

    protected PropertyWriter(BeanPropertyDefinition propDef) {
        super(propDef.getMetadata());
    }

    protected PropertyWriter(PropertyWriter base) {
        super(base);
    }

    /*
    /**********************************************************************
    /* Metadata access
    /**********************************************************************
     */

    @Override
    public abstract String getName();

    @Override
    public abstract PropertyName getFullName();

    /**
     * Convenience method for accessing annotation that may be associated
     * either directly on property, or, if not, via enclosing class (context).
     * This allows adding baseline contextual annotations, for example, by adding
     * an annotation for a given class and making that apply to all properties
     * unless overridden by per-property annotations.
     *<p>
     * This method is functionally equivalent to:
     *<pre>
     *  MyAnnotation ann = propWriter.getAnnotation(MyAnnotation.class);
     *  if (ann == null) {
     *    ann = propWriter.getContextAnnotation(MyAnnotation.class);
     *  }
     *</pre>
     * that is, tries to find a property annotation first, but if one is not
     * found, tries to find context-annotation (from enclosing class) of
     * same type.
     *
     * @since 2.5
     */
    public <A extends Annotation> A findAnnotation(Class<A> acls) {
        A ann = getAnnotation(acls);
        if (ann == null) {
            ann = getContextAnnotation(acls);
        }
        return ann;
    }

    /**
     * Method for accessing annotations directly declared for property that this
     * writer is associated with.
     *
     * @since 2.5
     */
    @Override
    public abstract <A extends Annotation> A getAnnotation(Class<A> acls);

    /**
     * Method for accessing annotations declared in context of the property that this
     * writer is associated with; usually this means annotations on enclosing class
     * for property.
     */
    @Override
    public abstract <A extends Annotation> A getContextAnnotation(Class<A> acls);

    /*
    /**********************************************************************
    /* Serialization methods, regular output
    /**********************************************************************
     */

    /**
     * The main serialization method called by filter when property is to be written
     * as an Object property.
     */
    public abstract void serializeAsProperty(Object value, JsonGenerator g, SerializationContext provider)
        throws Exception;

    /**
     * Serialization method that filter needs to call in cases where a property value
     * (key, value) is to be
     * filtered, but the underlying data format requires a placeholder of some kind.
     * This is usually the case for tabular (positional) data formats such as CSV.
     */
    public abstract void serializeAsOmittedProperty(Object value, JsonGenerator g, SerializationContext provider)
        throws Exception;

    /*
    /**********************************************************************
    /* Serialization methods, explicit positional/tabular formats
    /**********************************************************************
     */

    /**
     * Serialization method called when output is to be done as an array,
     * that is, not using property names. This is needed when serializing
     * container ({@link java.util.Collection}, array) types,
     * or POJOs using <code>tabular</code> ("as array") output format.
     *<p>
     * Note that this mode of operation is independent of underlying
     * data format; so it is typically NOT called for fully tabular formats such as CSV,
     * where logical output is still as form of POJOs.
     */
    public abstract void serializeAsElement(Object value, JsonGenerator g, SerializationContext provider)
        throws Exception;

    /**
     * Serialization method called when doing tabular (positional) output from databind,
     * but then value is to be omitted. This requires output of a placeholder value
     * of some sort; often similar to {@link #serializeAsOmittedProperty}.
     */
    public abstract void serializeAsOmittedElement(Object value, JsonGenerator g, SerializationContext provider)
        throws Exception;

    /*
    /**********************************************************************
    /* Schema-related
    /**********************************************************************
     */

    /**
     * Traversal method used for things like JSON Schema generation, or
     * POJO introspection.
     */
    @Override
    public abstract void depositSchemaProperty(JsonObjectFormatVisitor objectVisitor,
            SerializationContext provider);
}
