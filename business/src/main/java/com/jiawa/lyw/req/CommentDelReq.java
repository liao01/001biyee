package com.jiawa.lyw.req;

import lombok.Data;

import java.util.Date;

@Data
public class CommentDelReq {
    private Long id;

    private Long postId;

    private Long userId;

    private Long parentId;

    private Date createTime;

    private String content;
}