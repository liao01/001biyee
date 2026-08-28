package com.jiawa.lyw.service;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.RandomUtil;
import com.jiawa.lyw.Util.MailUtils;
import com.jiawa.lyw.domain.Member;
import com.jiawa.lyw.domain.SmsCode;
import com.jiawa.lyw.domain.SmsCodeExample;
import com.jiawa.lyw.enums.SmsCodeStatusEnum;
import com.jiawa.lyw.enums.SmsCodeUseEnum;
import com.jiawa.lyw.exception.BusinessException;
import com.jiawa.lyw.exception.BusinessExceptionEnum;
import com.jiawa.lyw.mapper.SmsCodeMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Slf4j
@Service
public class SmsCodeService {
    @Autowired
    private SmsCodeMapper smsCodeMapper;

    @Autowired
    private MemberService memberService;

    @Autowired
    private MailUtils mailUtils;

    public void sendCodeForRegister(String mobile){
        Member member = memberService.selectByMember(mobile);
        if (member != null){
            throw new BusinessException(BusinessExceptionEnum.MEMBER_MOBILE_HAD_REGISTER);
        }
        sendCode(mobile, SmsCodeUseEnum.REGISTER.getCode());
    }

    public void sendCodeForReset(String mobile){
        Member member = memberService.selectByMember(mobile);
        if (member == null){
            throw new BusinessException(BusinessExceptionEnum.MEMBER_MOBILE_NOT_REGISTER);
        }
        sendCode(mobile, SmsCodeUseEnum.RESET.getCode());
    }

    private void sendCode(String mobile, String use) {
        String code = RandomUtil.randomString(6);
        Date now = new Date();

        log.info("当前时间",now);
        log.info("1分钟以前", DateUtil.offsetMinute(now,-1));

        SmsCodeExample smsCodeExample = new SmsCodeExample();
        SmsCodeExample.Criteria criteria = smsCodeExample.createCriteria();
        criteria.andMobileEqualTo(mobile).andUseEqualTo(use).andCreatedAtGreaterThan(
                DateUtil.offsetMinute(now,-1)
        );

        long count = smsCodeMapper.countByExample(smsCodeExample);

        if (count > 0) {
            throw new BusinessException(BusinessExceptionEnum.SMS_CODE_TOO_FREQUENT);
        }

        SmsCode smsCode = new SmsCode();
        smsCode.setId(IdUtil.getSnowflakeNextId());
        smsCode.setMobile(mobile);
        smsCode.setCode(code);
        smsCode.setUse(use);
        smsCode.setStatus(SmsCodeStatusEnum.NOT_USED.getCode());
        smsCode.setCreatedAt(now);
        smsCode.setUpdatedAt(now);

        smsCodeMapper.insert(smsCode);

        //添加邮箱验证码
        log.info("准备发送邮件");
        mailUtils.sendMail(mobile,"你好，验证码是："+code,"旅分享");
        log.info("发送成功");
    }

    /**
     * 验证码校验
     * 5分钟内/同手机号/同用途/未使用的验证码才算有效
     * 只校验最后一次验证码
     */
    public void validCode(String mobile,String use,String code){
        Date now = new Date();

        SmsCodeExample smsCodeExample = new SmsCodeExample();
        SmsCodeExample.Criteria criteria = smsCodeExample.createCriteria();
        criteria.andMobileEqualTo(mobile)
                .andUseEqualTo(use)
                .andCreatedAtGreaterThan(DateUtil.offsetMinute(now,-5))
                .andStatusEqualTo(SmsCodeStatusEnum.NOT_USED.getCode());
        smsCodeExample.setOrderByClause("created_at desc");
        List<SmsCode> list = smsCodeMapper.selectByExample(smsCodeExample);
        if (CollUtil.isNotEmpty(list)){
            SmsCode smsCode = list.get(0);
            if (smsCode.getCode().equals(code)){
                smsCode.setStatus(SmsCodeStatusEnum.USED.getCode());
                smsCode.setUpdatedAt(now);
                smsCodeMapper.updateByPrimaryKeySelective(smsCode);
            }else {
                log.warn("验证码不正确，手机号:{},输入验证码:{},用途:{}",mobile,code,use);
                throw new BusinessException(BusinessExceptionEnum.SMS_CODE_ERROR);
            }
        }else {
            log.warn("验证码未发送或已过期，手机号:{},输入验证码:{},用途:{}",mobile,code,use);
            throw new BusinessException(BusinessExceptionEnum.SMS_CODE_EXPIRED);
        }

    }
}
