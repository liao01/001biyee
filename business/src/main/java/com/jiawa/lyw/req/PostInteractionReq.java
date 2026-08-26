package com.jiawa.lyw.req;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PostInteractionReq {
    @NotNull(message = "帖子ID不能为空")
    private Long postId;

    @NotNull(message = "互动状态不能为空")
    private Boolean active;
}
