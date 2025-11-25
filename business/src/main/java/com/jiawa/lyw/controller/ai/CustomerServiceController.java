package com.jiawa.lyw.controller.ai;

import com.alibaba.fastjson.JSONObject;
import com.jiawa.lyw.req.ai.MessageRequest;
import com.jiawa.lyw.resp.ai.ChatHistoryResp;
import com.jiawa.lyw.resp.ai.ResponseVO;
import com.jiawa.lyw.service.ai.ChatHistoryService;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/web/customerService")
public class CustomerServiceController {

    @Resource
    private RestTemplate restTemplate;

    @Autowired
    private ChatHistoryService chatHistoryService;

    @PostMapping("/message")
    public ResponseVO<String> receiveMessage(@RequestBody MessageRequest req) {

        // 构建请求体
        Map<String, Object> requestBody = new HashMap<>();
        String userMessage = req.getMessage();
        requestBody.put("message", "请使用中文简体回答,并控制字数在30字以内：" + userMessage);
        requestBody.put("mode", "chat");
        requestBody.put("userId", 1);

        // 构建请求头
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer NYZG9ME-PHPMMCC-NH8KJWH-GDBP3NE");
        headers.set("accept", "application/json");

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

        // Ollama API URL
        String url = "http://localhost:3001/api/v1/workspace/de/thread/bd8127f6-840d-437e-b3a4-85f46392ccd8/chat";

        // 调用 API
        String responseStr = restTemplate.postForObject(url, requestEntity, String.class);

        JSONObject jsonObject = JSONObject.parseObject(responseStr);
        String aiAnswer = (String) jsonObject.get("textResponse");

        // 如果返回包含 </think> 标签，取第二部分
        String content = aiAnswer.contains("</think>") ? aiAnswer.split("</think>")[1] : aiAnswer;

        chatHistoryService.saveChatHistory(userMessage,content);

        return ResponseVO.ok(content);
    }

    @GetMapping("/history")
    public ResponseVO<List<ChatHistoryResp>> findChatHistory(){
        List<ChatHistoryResp> chatHistory = chatHistoryService.findChatHistory();
        return new ResponseVO<>(200, "success",chatHistory) ;

    }


}