package com.jiawa.lyw.req;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class LocationDelReq {
    @NotNull(message = "id不能为空")
    private String id;
}
