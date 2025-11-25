package com.jiawa.lyw.resp;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PostResp {

    private String postId;        // 对应 post_id
    private String postTitle;   // 对应 post_title
    private String postContent; // 对应 post_content
    private String tagTitle;    // 对应 tag_title
    private String imageUrls;   // 对应 image_urls
    private String avatar;   // 对应 image_urls
     private String membername;   // 对应 membername
    private LocalDateTime postTime;
    private String userId;
}
