package ru.inversion.msmev.xxi.repo;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "mi_inf")
public class PInf {

    private int inf_id;
    private int wsp_id;
    private boolean initiator_cd;
    private String name, namespace;
    private String requestQueue;
    private String responseQueue;

    @Id
    @Column(name = "inf_id")
    public Integer getInfId() {
        return inf_id;
    }
    public void setInfId(Integer inf_id) {
        this.inf_id = inf_id;
    }

    @Column(name = "wsp_id")
    public Integer getWspId() {
        return wsp_id;
    }
    public void setWspId(Integer wsp_id) {
        this.wsp_id = wsp_id;
    }

    @Column(name = "initiator_cd")
    public Boolean isInitiator() {
        return initiator_cd;
    }
    public void setInitiator(Boolean initiator_cd) {
        this.initiator_cd = initiator_cd;
    }

    @Column(name = "name_inf")
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }

    @Column(name = "namespace_inf")
    public String getNamespace() {
        return namespace;
    }
    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    @Column(name = "request_queue")
    public String requestQueue() { return requestQueue; }
    public void setRequestQueue(String requestQueue) { this.requestQueue = requestQueue; }

    @Column(name = "response_queue")
    public String responseQueue(){ return responseQueue; }
    public void setResponseQueue(String responseQueue) { this.responseQueue = responseQueue; }
}
