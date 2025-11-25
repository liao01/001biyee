package com.jiawa.lyw.req;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AddressReq {
    private Integer id;
    @NotNull(message = "地址不能为空")
    private String address;
}
