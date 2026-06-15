package ru.inversion.msmev.js;

public class JSNotFoundException extends JSException {
    /** */
    public JSNotFoundException( String message, int infId, int type) {
        super(message, infId, type);
    }
    /** */
    @Override
    public String getDetailedMessage() {
        return String.format("JS скрипт типа %d не найден для вида сведений %d, в реестре mi_inf_js", getType(), getInfId() );
    }
}
