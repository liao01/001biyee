package com.jiawa.lyw.resp;

import lombok.Data;

import java.util.Date;

@Data
public class LocationRecordResp {
    private Long id;

    private Double longitude;

    private Double latitude;

    private String formattedAddress;

    private String city;

    private String province;

    private String district;

    private Date createTime;

}