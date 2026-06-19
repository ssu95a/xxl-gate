package ru.inversion.msmev.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Синхронный ответ XXL для XXI.
 *
 * <p>Поле {@code parameters} передаётся как JSON-текст внутри XML-элемента,
 * потому что {@code MI_resultCtx.result_From_Xml} в XXI преобразует его в jsonb.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JacksonXmlRootElement(localName = "XXLResponse")
public final class XXLResponse {

    public static final String VERSION = "1.0";
    public static final String DEFAULT_ACTION = "send";

    private static final ObjectMapper JSON_MAPPER = JsonMapper.builder().findAndAddModules().build();

    private final String version;
    private final String action;

    private final boolean success;

    private final String resultCode;
    private final String resultInfo;
    private final String details;

    private final String causeCode;
    private final String causeInfo;
    private final String causeDetails;

    private final String parametersJson;
    private final Map<String, Object> parameters;

    private XXLResponse(Builder builder) {
        this.version = textOrDefault( builder.version, VERSION );
        this.action  = textOrDefault( builder.action,  DEFAULT_ACTION );
        this.success = builder.success;

        this.resultCode = textOrDefault( builder.resultCode, builder.success ? "SUCCESS" : "ERROR" );
        this.resultInfo = textOrDefault(
                builder.resultInfo,
                builder.success ? "Completed successfully" : "Operation failed"
        );

        this.details = blankToNull(builder.details);
        this.causeCode = blankToNull(builder.causeCode);
        this.causeInfo = blankToNull(builder.causeInfo);
        this.causeDetails = blankToNull(builder.causeDetails);

        Map<String, Object> values = builder.parameters == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(builder.parameters));

        this.parameters = values;
        this.parametersJson = toJson(values);
    }

    @JacksonXmlProperty(isAttribute = true, localName = "version")
    public String getVersion() {
        return version;
    }

    @JacksonXmlProperty(isAttribute = true, localName = "action")
    public String getAction() {
        return action;
    }

    @JacksonXmlProperty(isAttribute = true, localName = "is_success")
    public boolean isSuccess() {
        return success;
    }

    @JacksonXmlProperty(isAttribute = true, localName = "result_code")
    public String getResultCode() {
        return resultCode;
    }

    @JacksonXmlProperty(isAttribute = true, localName = "result_info")
    public String getResultInfo() {
        return resultInfo;
    }

    @JacksonXmlProperty(localName = "details")
    public String getDetails() {
        return details;
    }

    @JacksonXmlProperty(isAttribute = true, localName = "cause_code")
    public String getCauseCode() {
        return causeCode;
    }

    @JacksonXmlProperty(isAttribute = true, localName = "cause_info")
    public String getCauseInfo() {
        return causeInfo;
    }

    @JacksonXmlProperty(localName = "cause_details")
    public String getCauseDetails() {
        return causeDetails;
    }

    /**
     * Именно этот getter сериализуется в XML как элемент {@code <parameters>}.
     */
    @JacksonXmlProperty(localName = "parameters")
    public String getParametersJson() {
        return parametersJson;
    }

    /**
     * Удобное представление параметров для Java-кода. В XML не сериализуется.
     */
    @JsonIgnore
    public Map<String, Object> getParameters() {
        return parameters;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Builder success() {
        return new Builder(true);
    }

    public static Builder fail() {
        return new Builder(false);
    }

    public static final class Builder {

        private String version = VERSION;
        private String action = DEFAULT_ACTION;
        private boolean success;

        private String resultCode;
        private String resultInfo;
        private String details;

        private String causeCode;
        private String causeInfo;
        private String causeDetails;

        private Map<String, Object> parameters;

        public Builder() {
        }

        private Builder(boolean success) {
            this.success = success;
        }

        public Builder version(String version) {
            this.version = version;
            return this;
        }

        public Builder action(String action) {
            this.action = action;
            return this;
        }

        public Builder success(boolean success) {
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
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("Parameter key must not be blank");
            }

            parameters().put(key, value);
            return this;
        }

        public Builder parameters(Map<String, Object> values) {
            if (values != null && !values.isEmpty()) {
                parameters().putAll(values);
            }
            return this;
        }

        public XXLResponse build() {
            return new XXLResponse(this);
        }

        private Map<String, Object> parameters() {
            if (parameters == null) {
                parameters = new LinkedHashMap<>();
            }
            return parameters;
        }
    }

    public static XXLResponse unsupportedOperation(
            String operationName,
            Map<String, Object> details
    ) {
        return fail()
                .resultCode("UNSUPPORTED_OPERATION")
                .resultInfo("Operation not supported: " + operationName)
                .causeCode("NOT_IMPLEMENTED")
                .causeInfo("The requested operation is not implemented in this context")
                .parameters(details)
                .build();
    }

    public static XXLResponse internalError(Throwable throwable) {
        Throwable error = throwable == null
                ? new IllegalStateException("Unknown internal XXL error")
                : throwable;

        return fail()
                .resultCode("XXL_INTERNAL_ERROR")
                .resultInfo("Internal XXL error")
                .causeCode(error.getClass().getSimpleName())
                .causeInfo(error.getMessage())
                .build();
    }

    public static XXLResponse fromException(Throwable throwable) {
        return fromException(throwable, false);
    }

    public static XXLResponse fromException(
            Throwable throwable,
            boolean includeStackTrace
    ) {
        Throwable error = throwable == null
                ? new IllegalStateException("Unexpected error")
                : throwable;

        String resultInfo = error.getMessage() == null
                ? "Unexpected error"
                : error.getMessage();

        Throwable cause = error.getCause();
        String stackTrace = null;

        if (includeStackTrace) {
            StringWriter writer = new StringWriter();
            error.printStackTrace(new PrintWriter(writer));
            stackTrace = writer.toString();
        }

        return fail()
                .resultCode(error.getClass().getSimpleName())
                .resultInfo(resultInfo)
                .causeCode(cause == null ? null : cause.getClass().getSimpleName())
                .causeInfo(cause == null ? null : cause.getMessage())
                .details(stackTrace)
                .build();
    }

    private static String toJson(Map<String, Object> values) {
        try {
            return JSON_MAPPER.writeValueAsString(values == null ? Map.of() : values);
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Failed to serialize XXLResponse.parameters to JSON",
                    exception
            );
        }
    }

    private static String textOrDefault(String value, String defaultValue) {
        String normalized = blankToNull(value);
        return normalized == null ? defaultValue : normalized;
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
