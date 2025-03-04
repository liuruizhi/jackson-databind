package tools.jackson.databind.exc;

import java.util.*;

import tools.jackson.core.TokenStreamLocation;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DatabindException;

/**
 * Specialized {@link DatabindException} sub-class used to indicate
 * case where an explicitly ignored property is encountered, and mapper
 * is configured to consider this an error.
 */
public class IgnoredPropertyException
    extends PropertyBindingException
{
    private static final long serialVersionUID = 1L;

    public IgnoredPropertyException(JsonParser p, String msg, TokenStreamLocation loc,
            Class<?> referringClass, String propName,
            Collection<Object> propertyIds)
    {
        super(p, msg, loc, referringClass, propName, propertyIds);
    }

    /**
     * Factory method used for constructing instances of this exception type.
     *
     * @param p Underlying parser used for reading input being used for data-binding
     * @param fromObjectOrClass Reference to either instance of problematic type (
     *    if available), or if not, type itself
     * @param propertyName Name of unrecognized property
     * @param propertyIds (optional, null if not available) Set of properties that
     *    type would recognize, if completely known: null if set cannot be determined.
     */
    public static IgnoredPropertyException from(JsonParser p,
            Object fromObjectOrClass, String propertyName,
            Collection<Object> propertyIds)
    {
        Class<?> ref;
        if (fromObjectOrClass instanceof Class<?>) {
            ref = (Class<?>) fromObjectOrClass;
        } else { // also acts as null check:
            ref = fromObjectOrClass.getClass();
        }
        String msg = String.format("Ignored field \"%s\" (class %s) encountered; mapper configured not to allow this",
                propertyName, ref.getName());
        IgnoredPropertyException e = new IgnoredPropertyException(p, msg,
                p.currentLocation(), ref, propertyName, propertyIds);
        // but let's also ensure path includes this last (missing) segment
        e.prependPath(fromObjectOrClass, propertyName);
        return e;
    }
}
