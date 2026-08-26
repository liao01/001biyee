package com.jiawa.lyw;

import com.jiawa.lyw.controller.ControllerExceptionHandler;
import com.jiawa.lyw.controller.TestController;
import com.jiawa.lyw.controller.web.MemberController;
import com.jiawa.lyw.exception.BusinessException;
import com.jiawa.lyw.exception.BusinessExceptionEnum;
import com.jiawa.lyw.resp.DemoQueryResp;
import com.jiawa.lyw.resp.MemberLoginResp;
import com.jiawa.lyw.service.KaptchaService;
import com.jiawa.lyw.service.MemberLoginLogService;
import com.jiawa.lyw.service.DemoService;
import com.jiawa.lyw.service.MemberService;
import com.jiawa.lyw.service.SmsCodeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class BusinessApplicationTests {

    private static final String TEST_EMAIL = "demo" + "@example.com";

    private MockMvc mockMvc;

    @Mock
    private DemoService demoService;

    @Mock
    private MemberService memberService;

    @Mock
    private SmsCodeService smsCodeService;

    @Mock
    private KaptchaService kaptchaService;

    @Mock
    private MemberLoginLogService memberLoginLogService;

    @BeforeEach
    void setUp() {
        TestController testController = new TestController();
        ReflectionTestUtils.setField(testController, "demoService", demoService);

        MemberController memberController = new MemberController();
        ReflectionTestUtils.setField(memberController, "memberService", memberService);
        ReflectionTestUtils.setField(memberController, "smsCodeService", smsCodeService);
        ReflectionTestUtils.setField(memberController, "kaptchaService", kaptchaService);
        ReflectionTestUtils.setField(memberController, "memberLoginLogService", memberLoginLogService);

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(testController, memberController)
                .setControllerAdvice(new ControllerExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void helloShouldReturnPlainText() throws Exception {
        mockMvc.perform(get("/hello"))
                .andExpect(status().isOk())
                .andExpect(content().string("hello"));
    }

    @Test
    void queryShouldReturnCommonResponseWhenServiceSucceeds() throws Exception {
        DemoQueryResp demo = new DemoQueryResp();
        demo.setId(1L);
        demo.setMobile(TEST_EMAIL);
        when(demoService.query(any())).thenReturn(List.of(demo));

        mockMvc.perform(get("/query").param("mobile", TEST_EMAIL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].mobile").value(TEST_EMAIL));
    }

    @Test
    void queryShouldReturnStableResponseWhenValidationFails() throws Exception {
        mockMvc.perform(get("/query"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void queryShouldReturnBusinessErrorWithoutBreakingResponseShape() throws Exception {
        when(demoService.query(any()))
                .thenThrow(new BusinessException(BusinessExceptionEnum.DEMO_MOBILE_NOT_NULL));

        mockMvc.perform(get("/query").param("mobile", TEST_EMAIL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(BusinessExceptionEnum.DEMO_MOBILE_NOT_NULL.getDesc()));
    }

    @Test
    void countShouldReturnGenericErrorWhenUnexpectedExceptionHappens() throws Exception {
        when(demoService.count()).thenThrow(new RuntimeException("database is unavailable"));

        mockMvc.perform(get("/count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void memberRegisterShouldReturnSuccessWhenRequestIsValid() throws Exception {
        String body = """
                {
                  "mobile": "%s",
                  "password": "a111111",
                  "code": "123456"
                }
                """.formatted(TEST_EMAIL);

        mockMvc.perform(post("/web/member/register")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void memberRegisterShouldReturnStableResponseWhenRequiredFieldMissing() throws Exception {
        String body = """
                {
                  "mobile": "%s",
                  "password": "",
                  "code": "123456"
                }
                """.formatted(TEST_EMAIL);

        mockMvc.perform(post("/web/member/register")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void memberLoginShouldReturnTokenWhenRequestIsValid() throws Exception {
        MemberLoginResp loginResp = new MemberLoginResp();
        loginResp.setId(1L);
        loginResp.setName("demo");
        loginResp.setToken("test-token");
        when(memberService.login(any())).thenReturn(loginResp);

        String body = """
                {
                  "mobile": "%s",
                  "password": "a111111",
                  "imageCode": "abcd",
                  "imageCodeToken": "token-123"
                }
                """.formatted(TEST_EMAIL);

        mockMvc.perform(post("/web/member/login")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.content.id").value(1))
                .andExpect(jsonPath("$.content.name").value("demo"))
                .andExpect(jsonPath("$.content.token").value("test-token"));
    }

    @Test
    void memberLoginShouldReturnBusinessErrorWhenCaptchaIsInvalid() throws Exception {
        org.mockito.Mockito.doThrow(new BusinessException(BusinessExceptionEnum.IMAGE_CODE_ERROR))
                .when(kaptchaService).validCode(any(), any());

        String body = """
                {
                  "mobile": "%s",
                  "password": "a111111",
                  "imageCode": "wrong",
                  "imageCodeToken": "token-123"
                }
                """.formatted(TEST_EMAIL);

        mockMvc.perform(post("/web/member/login")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(BusinessExceptionEnum.IMAGE_CODE_ERROR.getDesc()));
    }
}
