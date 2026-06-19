package ru.inversion.msmev.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import ru.inversion.utils.IDumpable;
import ru.inversion.utils.dco.IDco;

import java.util.Map;
import java.util.UUID;

@Value
@Builder
@Jacksonized
@JacksonXmlRootElement(localName = "XXLRequest")
public class XXLRequest implements IDumpable {

    @JacksonXmlProperty(isAttribute = true, localName = "version")
    String version;

    @JacksonXmlProperty(isAttribute = true, localName = "action")
    String action;

    @JacksonXmlProperty(isAttribute = true, localName = "mode")
    String mode;

    @JacksonXmlProperty(isAttribute = true, localName = "req_id")
    Long requestId;

    @JacksonXmlProperty(isAttribute = true, localName = "external_uuid")
    UUID externalUuid;

    @JacksonXmlProperty(isAttribute = true, localName = "inf_id")
    Integer infId;

    @JacksonXmlProperty(isAttribute = true, localName = "correlation_id")
    UUID correlationId;

    @JacksonXmlProperty(isAttribute = true, localName = "call_uuid")
    UUID callUuid;

    @JacksonXmlProperty(isAttribute = true, localName = "timestamp")
    String timestamp;

    @JsonIgnore
    IDco rawData;

    @Override
    public void dump( Map<String, Object> properties ) {

        if( properties == null )
            return;

        properties.put("version",      version );
        properties.put("action" ,      action );
        properties.put("mode"   ,      mode );
        properties.put("requestId",    requestId );
        properties.put("externalUuid", externalUuid );
        properties.put("infId",        infId );
        properties.put("correlationId",correlationId );
        properties.put("callUuid",     callUuid );
        properties.put("timestamp",    timestamp );
    }
}