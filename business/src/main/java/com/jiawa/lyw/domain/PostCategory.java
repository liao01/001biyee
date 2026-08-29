package com.jiawa.lyw.domain;

import lombok.Data;

import java.util.Date;

@Data
public class PostCategory {
    private String code;
    private String name;
    private Integer sortOrder;
    private Boolean enabled;
    private Date createTime;
    private Date updateTime;
}
