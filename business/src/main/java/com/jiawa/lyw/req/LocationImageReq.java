package com.jiawa.lyw.req;

import lombok.Data;

import java.util.Date;

@Data
public class LocationImageReq {
    private Long id;

    private Long locationId;

    private String imageUrl;

    private Integer seq;

    private String description;

    private Date createTime;
}