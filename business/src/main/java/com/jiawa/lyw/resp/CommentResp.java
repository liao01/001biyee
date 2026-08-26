package com.jiawa.lyw.resp;

import lombok.Data;

import java.util.Date;

@Data
public class CommentResp {
    private String id;
    private String userId;
    private String commentContent; // 评论内容
    private String membername;     // 用户名
    private Date commentTime;
    private String avatar;
}
