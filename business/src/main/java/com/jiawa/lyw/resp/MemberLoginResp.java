package com.jiawa.lyw.resp;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

@Data
public class MemberLoginResp {
    private String name;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String token;
}
