package com.jiawa.lyw.req;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Date;

@Data
public class UserActionReq {
    private Long id;

    private Long userId;

    @NotNull(message = "帖子不能唯恐欧冠")
    private Long postId;

    private String actionType;

    private Date createTime;

}