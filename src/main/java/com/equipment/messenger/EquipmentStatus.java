package com.equipment.messenger;

import java.util.Date;

/**
 * 装置ステータスデータモデル
 */
public class EquipmentStatus {
    private String eqpId;
    private String status;
    private Date timestampTime;

    public EquipmentStatus() {
    }

    public EquipmentStatus(String eqpId, String status, Date timestampTime) {
        this.eqpId = eqpId;
        this.status = status;
        this.timestampTime = timestampTime;
    }

    public String getEqpId() {
        return eqpId;
    }

    public void setEqpId(String eqpId) {
        this.eqpId = eqpId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Date getTimestampTime() {
        return timestampTime;
    }

    public void setTimestampTime(Date timestampTime) {
        this.timestampTime = timestampTime;
    }

    @Override
    public String toString() {
        return "EquipmentStatus{" +
                "eqpId='" + eqpId + '\'' +
                ", status='" + status + '\'' +
                ", timestampTime=" + timestampTime +
                '}';
    }
}
