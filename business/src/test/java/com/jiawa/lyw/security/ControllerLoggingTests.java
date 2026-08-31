package com.jiawa.lyw.security;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.jiawa.lyw.aspect.LogAspect;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ControllerLoggingTests {
    @Test
    void httpLoggingNeverSerializesRequestResponseOrScalarCredentials() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(LogAspect.class);
        ListAppender<ILoggingEvent> logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);
        try {
            AspectJProxyFactory proxy = new AspectJProxyFactory(new LoggingProbeController());
            proxy.addAspect(new LogAspect());
            LoggingProbeController controller = proxy.getProxy();
            var mvc = MockMvcBuilders.standaloneSetup(controller).build();
            String submittedSecret = UUID.randomUUID().toString();
            String submittedLink = UUID.randomUUID().toString();

            mvc.perform(post("/logging-probe")
                            .param("token", submittedLink)
                            .contentType("application/json")
                            .content("{\"password\":\"" + submittedSecret + "\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").value(submittedSecret));

            assertTrue(logs.list.size() > 0, "Keep useful request metadata logging");
            for (ILoggingEvent event : logs.list) {
                assertFalse(event.getFormattedMessage().contains(submittedSecret));
                assertFalse(event.getFormattedMessage().contains(submittedLink));
            }
        } finally {
            logger.detachAppender(logs);
            logs.stop();
        }
    }

    @RestController
    static class LoggingProbeController {
        @PostMapping("/logging-probe")
        public Map<String, String> exchange(@RequestBody Map<String, String> input, @RequestParam String token) {
            return Map.of("accessToken", input.get("password"), "refreshToken", token);
        }
    }
}
