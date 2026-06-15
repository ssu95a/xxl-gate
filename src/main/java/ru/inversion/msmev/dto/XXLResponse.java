package ru.inversion.msmev.dto;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

@AllArgsConstructor
@Getter
public class XXLResponse {

    @JacksonXmlProperty( isAttribute = true, localName = "is_success" )
    private final boolean success;

    @JacksonXmlProperty( isAttribute = true, localName = "action" )
    private final String action;

    @JacksonXmlProperty( isAttribute = true, localName = "result_code" )
    private final String resultCode;

    @JacksonXmlProperty( isAttribute = true, localName = "result_info" )
    private final String resultInfo;

    @JacksonXmlProperty( localName = "details" )
    private final String details;

    @JacksonXmlProperty( isAttribute = true, localName = "cause_code" )
    private final String causeCode;

    @JacksonXmlProperty( isAttribute = true, localName = "cause_info" )
    private final String causeInfo;

    @JacksonXmlProperty( localName = "cause_details" )
    private final String causeDetails;

    @JacksonXmlProperty( localName = "parameters" )
    private final Map<String, Object> parameters;

    public static Builder builder() {
        return new Builder();
    }
    public static Builder success() {
        return new Builder(true);
    }
    public static Builder fail()    { return new Builder(false);}

    /** */
    public static class Builder {

        private boolean success;

        private String action;

        private String resultCode;
        private String resultInfo;
        private String details;

        private String causeCode;
        private String causeInfo;
        private String causeDetails;

        private Map<String, Object> parameters;

        /** */
        public Builder()
        { }

        /** */
        public Builder(boolean success) {
            this.success = success;
        }

        /** */
        public Builder action( String action ) {
            this.action = action;
            return this;
        }

        /** */
        public Builder success(boolean success ) {
            this.success = success;
            return this;
        }

        public Builder resultCode(String resultCode) {
            this.resultCode = resultCode;
            return this;
        }

        public Builder resultInfo(String resultInfo) {
            this.resultInfo = resultInfo;
            return this;
        }

        public Builder details(String details) {
            this.details = details;
            return this;
        }

        public Builder causeCode(String causeCode) {
            this.causeCode = causeCode;
            return this;
        }

        public Builder causeInfo(String causeInfo) {
            this.causeInfo = causeInfo;
            return this;
        }

        public Builder causeDetails(String causeDetails) {
            this.causeDetails = causeDetails;
            return this;
        }

        public Builder parameter(String key, Object value) {

            if( this.parameters == null )
                this.parameters = new HashMap<>();

            this.parameters.put(key, value);

            return this;
        }

        /** */
        public Builder parameters( Map<String, Object> parameters )
        {
            if( this.parameters == null )
                this.parameters = new HashMap<>();

            this.parameters.putAll( parameters );

            return this;
        }

        /** */
        public XXLResponse build() {
            // Проверка обязательных полей? Можно добавить, но не обязательно
            return new XXLResponse( success, action, resultCode, resultInfo, details, causeCode, causeInfo, causeDetails, parameters);
        }
    }

    /** */
    public static XXLResponse unsupportedOperation( String operationName, Map<String, Object> details ) {
        return fail()
                .resultCode("UNSUPPORTED_OPERATION")
                    .resultInfo("Operation not supported: " + operationName)
                .causeCode("NOT_IMPLEMENTED")
                    .causeInfo("The requested operation is not implemented in this context")
                .parameters(details)
            .build();
    }

    /** */
    public static XXLResponse internalError( Throwable th ) {
        return fail()
            .resultCode( "XXL_INTERNAL_ERROR" )
                .resultInfo( "Internal XXL error" )
            .causeCode( th.getClass().getSimpleName() )
                .causeInfo( th.getMessage() )
        .build();
    }

    /** */
    public static XXLResponse fromException(Throwable th) {
        return fromException(th, false);
    }

    /** */
    public static XXLResponse fromException(Throwable th, boolean includeStackTrace) {

        String resultCode = th.getClass().getSimpleName();
        String resultInfo = th.getMessage() != null ? th.getMessage() : "Unexpected error";

        String causeCode = null;
        String causeInfo = null;
        if( th.getCause() != null ) {
            causeCode = th.getCause().getClass().getSimpleName();
            causeInfo = th.getCause().getMessage();
        }

        String details = null;

        if( includeStackTrace ) {
            StringWriter sw = new StringWriter();
            th.printStackTrace(new PrintWriter(sw));
            details = sw.toString();
        }

        return fail().resultCode(resultCode).resultInfo(resultInfo).causeCode(causeCode).causeInfo(causeInfo).details(details).build();
    }
}