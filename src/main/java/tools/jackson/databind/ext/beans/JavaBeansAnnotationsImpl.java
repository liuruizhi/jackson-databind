package tools.jackson.databind.ext.beans;

import java.beans.ConstructorProperties;
import java.beans.Transient;

import tools.jackson.databind.PropertyName;
import tools.jackson.databind.introspect.Annotated;
import tools.jackson.databind.introspect.AnnotatedParameter;
import tools.jackson.databind.introspect.AnnotatedWithParams;

public class JavaBeansAnnotationsImpl extends JavaBeansAnnotations
{
    public final static JavaBeansAnnotationsImpl instance = new JavaBeansAnnotationsImpl();
    
    @SuppressWarnings("unused") // compiler warns, just needed side-effects
    private final Class<?> _bogus;

    public JavaBeansAnnotationsImpl() {
        // Trigger loading of annotations that only JDK 7 has, to trigger
        // early fail (see [databind#2466])
        Class<?> cls = Transient.class;
        cls = ConstructorProperties.class;
        _bogus = cls;
    }

    @Override
    public Boolean findTransient(Annotated a) {
        Transient t = a.getAnnotation(Transient.class);
        if (t != null) {
            return t.value();
        }
        return null;
    }

    @Override
    public Boolean hasCreatorAnnotation(Annotated a) {
        ConstructorProperties props = a.getAnnotation(ConstructorProperties.class);
        // 08-Nov-2015, tatu: One possible check would be to ensure there is at least
        //    one name iff constructor has arguments. But seems unnecessary for now.
        if (props != null) {
            return Boolean.TRUE;
        }
        return null;
    }

    @Override
    public PropertyName findConstructorName(AnnotatedParameter p)
    {
        AnnotatedWithParams ctor = p.getOwner();
        if (ctor != null) {
            ConstructorProperties props = ctor.getAnnotation(ConstructorProperties.class);
            if (props != null) {
                String[] names = props.value();
                int ix = p.getIndex();
                if (ix < names.length) {
                    return PropertyName.construct(names[ix]);
                }
            }
        }
        return null;
    }
}
