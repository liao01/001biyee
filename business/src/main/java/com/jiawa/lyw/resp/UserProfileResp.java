package com.jiawa.lyw.resp;

import lombok.Data;

import java.util.Date;

@Data
public class UserProfileResp {

    private String userid;

    private String username;

    private String avatar;

    private Byte gender;

    private Date birthday;

    private String bio;

    private String location;

}