package com.jiawa.lyw.service.ai;

import cn.hutool.core.util.IdUtil;
import com.jiawa.lyw.context.LoginMemberContext;
import com.jiawa.lyw.domain.ChatHistory;
import com.jiawa.lyw.mapper.ChatHistoryMapper;
import com.jiawa.lyw.mapper.ChatHistoryMapperCust;
import com.jiawa.lyw.resp.ai.ChatHistoryResp;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Service
public class ChatHistoryService {
    @Autowired
    private ChatHistoryMapper chatHistoryMapper;
    @Autowired
    private ChatHistoryMapperCust chatHistoryMapperCust;

    public void saveChatHistory(String userMessage, String aiResponse) {
        ChatHistory chatHistory = new ChatHistory();
        chatHistory.setId(IdUtil.getSnowflakeNextId());
        chatHistory.setUserId(LoginMemberContext.getId());
        chatHistory.setUserMessage(userMessage);
        chatHistory.setAiResponse(aiResponse);
        chatHistory.setCreateTime(new Date());
        chatHistoryMapper.insert(chatHistory);
    }

    public List<ChatHistoryResp> findChatHistory() {
        List<ChatHistoryResp> chatHistoryList = chatHistoryMapperCust.selectByUserId(LoginMemberContext.getId());


        return chatHistoryList;
    }

}
