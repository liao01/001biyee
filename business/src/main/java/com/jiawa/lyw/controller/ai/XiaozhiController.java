package com.jiawa.lyw.controller.ai;

import com.jiawa.lyw.assistant.Assistant;
import com.jiawa.lyw.domain.ChatForm;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@Slf4j
@RestController
@RequestMapping("/web/xiaozhi")
public class XiaozhiController {

    @Autowired
    private Assistant assistant;

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chat(@RequestBody ChatForm chatForm) {
        Flux<String> flux = assistant.chat(
                Math.toIntExact(chatForm.getMemoryId()),
                chatForm.getMessage()
        );

        // 每条消息打印一次，不消费 Flux
        return flux.doOnNext(message -> {
            System.out.println("AI 返回: " + message);
            log.info("AI 返回: {}", message);
        });
    }
}