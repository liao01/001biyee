package com.jiawa.lyw.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PostSearchReq {
    @NotBlank(message = "关键词不能为空")
    private String keyword; // 搜索关键词
}
