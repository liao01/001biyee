package com.jiawa.lyw.security;

import com.jiawa.lyw.config.CorsConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = IdentityCorsTests.Config.class)
@TestPropertySource(properties = "app.public-url=https://travel.example.test/travel")
class IdentityCorsTests {
    @Autowired WebApplicationContext context;

    @Test
    void onlyTheConfiguredFrontendOriginCanUseCredentialedRequests() throws Exception {
        var mvc = MockMvcBuilders.webAppContextSetup(context).build();
        mvc.perform(options("/web/identity/refresh").header("Origin", "https://travel.example.test")
                .header("Access-Control-Request-Method", "POST").header("Access-Control-Request-Headers", "content-type"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", "https://travel.example.test"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
        for (String origin : new String[]{"https://untrusted.example.test", "null", "https://travel.example.test.attacker.invalid"}) {
            mvc.perform(post("/web/identity/refresh").header("Origin", origin))
                    .andExpect(status().isForbidden()).andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
        }
    }

    @Configuration @EnableWebMvc @Import({CorsConfig.class, Probe.class})
    static class Config { }
    @RestController
    static class Probe {
        @PostMapping("/web/identity/refresh") String refresh() { return "TEST controller reached"; }
    }
}
