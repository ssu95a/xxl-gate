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
@Table(name = "mi_rsp")
@Setter
public class PRsp implements IDumpable {

    private Long responseId;
    private Long requestId;
    private Long itemId;

    private UUID responseUuid;

    private Integer status;

    private String categoryCode;
    private String resultCode;
    private String resultInfo;

    @Id
    @Column(name = "rsp_id")
    public Long getResponseId() {
        return responseId;
    }

    @Column(name = "req_id")
    public Long getRequestId() {
        return requestId;
    }

    @Column(name = "itm_id")
    public Long getItemId() {
        return itemId;
    }

    @Column(name = "status_cd")
    public Integer getStatus() {
        return status;
    }

    @Column(name = "rsp_uuid")
    public UUID getResponseUuid(){ return responseUuid; }

    @Column(name = "category_cd")
    public String getCategoryCode() {
        return categoryCode;
    }

    @Column(name = "result_code")
    public String getResultCode() {
        return resultCode;
    }

    @Column(name = "result_info")
    public String getResultInfo() {
        return resultInfo;
    }

    @Override
    public void dump( Map<String, Object> properties )
    {
        properties.put( "req_id",    getRequestId() );
        properties.put( "status_cd", getStatus()    );
    }
}
