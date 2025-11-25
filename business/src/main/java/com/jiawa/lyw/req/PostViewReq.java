package com.jiawa.lyw.req;

import lombok.Data;

import java.util.Date;

@Data
public class PostViewReq {
    private Long userId;

    private Long postId;

    private Date viewTime;
}