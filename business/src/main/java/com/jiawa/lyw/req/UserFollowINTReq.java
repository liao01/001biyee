package com.jiawa.lyw.req;

import lombok.Data;

@Data
public class UserFollowINTReq {
    private Long userId;
    private String username;
    private Integer followStatus;
}