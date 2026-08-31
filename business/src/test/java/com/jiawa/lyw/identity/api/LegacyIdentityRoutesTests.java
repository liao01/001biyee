package com.jiawa.lyw.identity.api;

import com.jiawa.lyw.controller.web.MemberController;
import com.jiawa.lyw.controller.web.SmsCodeController;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LegacyIdentityRoutesTests {
    @ParameterizedTest
    @ValueSource(strings = {"/web/member/register", "/web/member/login", "/web/member/reset",
            "/web/sms-code/send-for-register", "/web/sms-code/send-for-reset"})
    void removedIdentityPathsHaveNoHttpHandler(String path) throws Exception {
        // 不注入任何旧服务，避免关闭入口的回归测试触发邮件或历史数据写入。
        var mvc = MockMvcBuilders.standaloneSetup(new MemberController(), new SmsCodeController()).build();
        mvc.perform(post(path).contentType("application/json").content("{}"))
                .andExpect(status().isNotFound());
    }
}
