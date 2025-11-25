package com.jiawa.lyw.resp;

import lombok.Data;

import java.util.Date;

@Data
public class PostViewResp {
    private String postId;
    private String postTitle;
    private String postContent;
    private String membername;
    private String imageUrls;
    private Date lastViewTime;
    private String userId;
    private String avatar;
}
