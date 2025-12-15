package com.jiawa.lyw.domain;

import java.util.Date;

public class MemberLoginLog {
    private Long id;

    private Long memberId;

    private Date loginTime;

    private String token;

    private Integer heartCount;

    private Date lastHeartTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getMemberId() {
        return memberId;
    }

    public void setMemberId(Long memberId) {
        this.memberId = memberId;
    }

    public Date getLoginTime() {
        return loginTime;
    }

    public void setLoginTime(Date loginTime) {
        this.loginTime = loginTime;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public Integer getHeartCount() {
        return heartCount;
    }

    public void setHeartCount(Integer heartCount) {
        this.heartCount = heartCount;
    }

    public Date getLastHeartTime() {
        return lastHeartTime;
    }

    public void setLastHeartTime(Date lastHeartTime) {
        this.lastHeartTime = lastHeartTime;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().getSimpleName());
        sb.append(" [");
        sb.append("Hash = ").append(hashCode());
        sb.append(", id=").append(id);
        sb.append(", memberId=").append(memberId);
        sb.append(", loginTime=").append(loginTime);
        sb.append(", token=").append(token);
        sb.append(", heartCount=").append(heartCount);
        sb.append(", lastHeartTime=").append(lastHeartTime);
        sb.append("]");
        return sb.toString();
    }
}