package com.jiawa.lyw.resp;

import lombok.Data;

import java.util.List;

@Data
public class LocationRecordResp {
    /** 主键ID */
    private String id;

    /** 完整地址 */
    private String formattedAddress;

    /** 城市 */
    private String city;

    private Double longitude;

    private Double latitude;

    /** 省份 */
    private String province;

    /** 区县 */
    private String district;

    /** 地点名称 */
    private String name;

    /** 地点描述 */
    private String description;

    /** 数据库字段（不直接给前端） */
    private String imageUrls;

    /** 给前端使用的图片列表 */
    private List<String> imageUrlList;

}