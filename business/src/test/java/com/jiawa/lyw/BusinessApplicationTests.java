package com.jiawa.lyw;

import com.jiawa.lyw.controller.ControllerExceptionHandler;
import com.jiawa.lyw.controller.TestController;
import com.jiawa.lyw.exception.BusinessException;
import com.jiawa.lyw.exception.BusinessExceptionEnum;
import com.jiawa.lyw.resp.DemoQueryResp;
import com.jiawa.lyw.service.DemoService;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class BusinessApplicationTests {

    private MockMvc mockMvc;

    @Mock
    private DemoService demoService;

    @BeforeEach
    void setUp() {
        TestController testController = new TestController();
        ReflectionTestUtils.setField(testController, "demoService", demoService);

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(testController)
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
        demo.setMobile("demo@example.com");
        when(demoService.query(any())).thenReturn(List.of(demo));

        mockMvc.perform(get("/query").param("mobile", "demo@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].mobile").value("demo@example.com"));
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

        mockMvc.perform(get("/query").param("mobile", "demo@example.com"))
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

}
