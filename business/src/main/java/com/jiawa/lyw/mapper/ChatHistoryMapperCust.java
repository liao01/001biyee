package com.jiawa.lyw.mapper;

import com.jiawa.lyw.resp.ai.ChatHistoryResp;

import java.util.List;

public interface ChatHistoryMapperCust {
    List<ChatHistoryResp> selectByUserId(Long id);

}
