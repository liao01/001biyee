package com.jiawa.lyw.resp.ai;

import lombok.Data;

import java.util.Date;

@Data
public class ChatHistoryResp {
    private Long id;


    private Date createTime;

    private String userMessage;

    private String aiResponse;

}