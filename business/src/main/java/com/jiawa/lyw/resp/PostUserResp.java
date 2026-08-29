package com.jiawa.lyw.resp;

import lombok.Data;

@Data
public class PostUserResp {
    private String postId;           // 帖子ID
    private String postTitle;      // 帖子标题
    private String postContent;    // 帖子内容
    private String memberName;     // 作者昵称
    private String postTime;       // 发布时间（格式：yyyy-MM-dd HH:mm:ss）
    private String tagTitle;       // 标签列表（用逗号拼接）
    private String imageUrls;      // 图片URL列表（用逗号拼接）
    private String avatar;   // 对应 image_urls
    private String categoryCode;
    private String categoryName;
}
