package com.jiawa.lyw.req;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UserPostQueryReq {
    @NotNull(message = "userid不能为空")
    private String userid;
}
