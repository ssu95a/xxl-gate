package ru.inversion.edo.xxl.xxi.repo;

import lombok.Setter;
import ru.inversion.utils.IDumpable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "mi_req")
@Setter
public class PReq implements IDumpable {

    private Long    requestId;
    private UUID    correlationId, external_uuid, originalRequestUuid;
    private Integer status;
    private Integer infId;
    private Integer type;

//    private Integer i1, i2, i3;
//
//    private String  cTax_reqId;

    @Id
    @Column(name = "req_id")
    public Long getRequestId() {
        return requestId;
    }

    @Column(name = "correlation_id")
    public UUID getCorrelationId() {
        return correlationId;
    }

    @Column(name = "status_cd")
    public Integer getStatus() {
        return status;
    }

    @Column(name = "external_uuid")
    public UUID getExternalUuid() {
        return external_uuid;
    }
    public void setExternalUuid(UUID v) {
        external_uuid = v;
    }

    @Column(name = "inf_id")
    public Integer getInfId() {
        return infId;
    }

    @Column(name = "original_request_uuid")
    public UUID getOriginalRequestUuid()
    {
        return originalRequestUuid;
    }

    @Column(name = "itype")
    public Integer getType() {
        return type;
    }

//    @Column(name = "i1")
//    public Integer getI1() {
//        return i1;
//    }
//    @Column(name = "i2")
//    public Integer getI2() {
//        return i2;
//    }
//    @Column(name = "i3")
//    public Integer getI3() {
//        return i3;
//    }


    @Override
    public void dump( Map<String, Object> properties )
    {
        properties.put( "req_id",         getRequestId() );
        properties.put( "external_uuid",  getExternalUuid() );
        properties.put( "inf_id",         getInfId() );
        properties.put( "status_cd",      getStatus());
        properties.put( "correlation_id", getCorrelationId() );
    }
}
