package com.jiawa.lyw.resp;

import lombok.Data;

import java.util.Date;

@Data
public class UserActionResp {
    private String id;

    private String userId;

    private String postId;

    private String actionType;

    private Date createTime;

}