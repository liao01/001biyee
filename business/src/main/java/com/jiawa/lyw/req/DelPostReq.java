package com.jiawa.lyw.req;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DelPostReq {
    @NotNull(message = "帖子不存在!!!")
    private Long postId;
}
