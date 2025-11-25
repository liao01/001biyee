package com.jiawa.lyw.resp;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserFollowPesp {
    private Long followId;
    private String username;
    private LocalDateTime createTime;


}