package ru.inversion.msmev.js;

import ru.inversion.utils.IExceptionInfo;

/** */
public class JSException extends RuntimeException implements IExceptionInfo {

    final private int infId, type;

    /** */
    public JSException( String message, int infId, int type) {
        super( message );
        this.infId = infId;
        this.type = type;
    }

    /** */
    public JSException( String message, int infId, int type, Throwable th) {
        super( message, th );
        this.infId = infId;
        this.type = type;
    }

    /** */
    public int getInfId() {
        return infId;
    }

    /** */
    public int getType() {
        return type;
    }

    /** */
    @Override
    public String getCategory() {
        return "xxl.js";
    }
}
