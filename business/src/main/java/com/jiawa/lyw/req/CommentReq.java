package com.jiawa.lyw.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Date;

@Data
public class CommentReq {
    private Long id;

    private Long postId;

    private Long userId;

    private Long parentId;

    private Date createTime;
    @NotBlank(message = "内容不能为空")
    private String content;
}