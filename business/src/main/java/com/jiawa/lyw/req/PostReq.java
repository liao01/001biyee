package com.jiawa.lyw.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.hibernate.validator.constraints.NotEmpty;

import java.util.List;

@Data
public class PostReq {

    private Long userId;       // 作者ID
    @NotBlank(message = "标题不能为空")
    private String title;      // 帖子标题
    @NotBlank(message = "内容不能为空")
    private String content;    // 帖子内容
    private Long locationId;   // 关联地点ID
    private Integer status;    // 0 草稿, 1 公开, 2 删除
    @NotEmpty(message = "图片不能为空")
    private List<PostImage> images;  // 多张图片列表

    @NotEmpty(message = "标签不能为空")
    private List<TagDTO> tags;

    @Data
    public static class PostImage {
        private String imageUrl;     // 图片URL
        private Integer seq;         // 图片顺序
        private String description;  // 图片描述
    }

    @Data
    public static class TagDTO {
        private String name;
    }

}
