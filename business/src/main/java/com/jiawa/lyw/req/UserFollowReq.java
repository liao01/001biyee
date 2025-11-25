package com.jiawa.lyw.req;

import lombok.Data;

import java.util.Date;

@Data
public class UserFollowReq {
    private Long id;

    private Long userId;

    private Long followId;

    private Date createTime;
}