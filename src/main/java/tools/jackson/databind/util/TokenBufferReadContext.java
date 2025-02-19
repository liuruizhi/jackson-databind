package tools.jackson.databind.util;

import tools.jackson.core.*;
import tools.jackson.core.io.ContentReference;
import tools.jackson.core.json.JsonReadContext;

/**
 * Implementation of {@link TokenStreamContext} used by {@link TokenBuffer}
 * to link back to the original context to try to keep location information
 * consistent between source location and buffered content when it's re-read
 * from the buffer.
 */
public class TokenBufferReadContext extends TokenStreamContext
{
    protected final TokenStreamContext _parent;

    protected final TokenStreamLocation _startLocation;

    // Benefit for reusing?
//    protected JsonReadContext _child;

    /*
    /**********************************************************************
    /* Location/state information (minus source reference)
    /**********************************************************************
     */

    protected String _currentName;

    protected Object _currentValue;

    protected TokenBufferReadContext(TokenStreamContext base, ContentReference contentRef)
    {
        super(base);
        _parent = base.getParent();
        _currentName = base.currentName();
        _currentValue = base.currentValue();
        if (base instanceof JsonReadContext) {
            JsonReadContext rc = (JsonReadContext) base;
            _startLocation = rc.startLocation(contentRef);
        } else {
            _startLocation = TokenStreamLocation.NA;
        }
    }

    protected TokenBufferReadContext(TokenStreamContext base, TokenStreamLocation startLoc) {
        super(base);
        _parent = base.getParent();
        _currentName = base.currentName();
        _currentValue = base.currentValue();
        _startLocation = startLoc;
    }

    /**
     * Constructor for case where there is no real surrounding context: just create
     * virtual ROOT
     */
    protected TokenBufferReadContext() {
        super(TYPE_ROOT, -1);
        _parent = null;
        _startLocation = TokenStreamLocation.NA;
    }

    protected TokenBufferReadContext(TokenBufferReadContext parent, int type, int index) {
        super(type, index);
        _parent = parent;
        _startLocation = parent._startLocation;
    }

    @Override
    public Object currentValue() {
        return _currentValue;
    }

    @Override
    public void assignCurrentValue(Object v) {
        _currentValue = v;
    }

    /*
    /**********************************************************************
    /* Factory methods
    /**********************************************************************
     */

    public static TokenBufferReadContext createRootContext(TokenStreamContext origContext) {
        // First: possible to have no current context; if so, just create bogus ROOT context
        if (origContext == null) {
            return new TokenBufferReadContext();
        }
        return new TokenBufferReadContext(origContext, ContentReference.unknown());
    }

    public TokenBufferReadContext createChildArrayContext() {
        // For current context there will be one next Array value, first:
        ++_index;
        return new TokenBufferReadContext(this, TYPE_ARRAY, -1);
    }

    public TokenBufferReadContext createChildObjectContext() {
        // For current context there will be one next Object value, first:
        ++_index;
        return new TokenBufferReadContext(this, TYPE_OBJECT, -1);
    }

    /**
     * Helper method we need to handle discontinuity between "real" contexts buffer
     * creates, and ones from parent: problem being they are of different types.
     */
    public TokenBufferReadContext parentOrCopy() {
        // 30-Apr-2017, tatu: This is bit awkward since part on ancestor stack is of different
        //     type (usually `JsonReadContext`)... and so for unbalanced buffers (with extra
        //     END_OBJECT / END_ARRAY), we may need to create
        if (_parent instanceof TokenBufferReadContext) {
            return (TokenBufferReadContext) _parent;
        }
        if (_parent == null) { // unlikely, but just in case let's support
            return new TokenBufferReadContext();
        }
        return new TokenBufferReadContext(_parent, _startLocation);
    }

    /*
    /**********************************************************************
    /* Abstract method implementation
    /**********************************************************************
     */

    @Override public String currentName() { return _currentName; }

    @Override public boolean hasCurrentName() { return _currentName != null; }

    @Override public TokenStreamContext getParent() { return _parent; }

    public void setCurrentName(String name) {
        _currentName = name;
    }

    /*
    /**********************************************************************
    /* Extended support for context updates
    /**********************************************************************
     */

    public void updateForValue() {
        ++_index;
    }
}
