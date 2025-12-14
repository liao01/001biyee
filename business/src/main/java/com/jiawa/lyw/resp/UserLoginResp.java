package com.jiawa.lyw.resp;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

@Data
public class UserLoginResp {
    private String LoginName;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String token;
}
