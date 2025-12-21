package com.jiawa.lyw.req;

import lombok.Data;

@Data
public class AddressReq {
    private Long id;

    private String formattedAddress;

    private String name;

    private String description;
}
