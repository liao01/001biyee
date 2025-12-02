package com.jiawa.lyw.controller.ai;

import com.jiawa.lyw.assistant.Assistant;
import com.jiawa.lyw.domain.ChatForm;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/web/xiaozhi")
public class XiaozhiController {

    @Autowired
    private Assistant assistant;
    @PostMapping(value = "/chat")
    public String chat(@RequestBody ChatForm chatForm)  {
        return assistant.chat(Math.toIntExact(chatForm.getMemoryId()), chatForm.getMessage());
    }
}