package com.jiawa.lyw.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.IdUtil;
import com.jiawa.lyw.Util.JwtUtil;
import com.jiawa.lyw.domain.Member;
import com.jiawa.lyw.domain.MemberExample;
import com.jiawa.lyw.exception.BusinessException;
import com.jiawa.lyw.exception.BusinessExceptionEnum;
import com.jiawa.lyw.mapper.MemberMapper;
import com.jiawa.lyw.req.MemberLoginReq;
import com.jiawa.lyw.req.MemberRegisterReq;
import com.jiawa.lyw.req.MemberResetReq;
import com.jiawa.lyw.resp.MemberLoginResp;
import com.jiawa.lyw.resp.StatisticResp;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class MemberService {
    @Autowired
    private MemberMapper memberMapper;
    @Resource
    private MemberLoginLogService  memberLoginLogService;

    //按手机号查会员信息
    public Member selectByMember(String member){
        MemberExample example = new MemberExample();
        MemberExample.Criteria criteria = example.createCriteria();
        criteria.andMobileEqualTo(member);
        List<Member> list = memberMapper.selectByExample(example);
        if (CollUtil.isNotEmpty(list)) {
            return list.get(0);
        }else{
            return null;
        }
    }

    public List<Member> findByMemberId(Long memberId){
        MemberExample example = new MemberExample();
        MemberExample.Criteria criteria = example.createCriteria();
        criteria.andIdEqualTo(memberId);
        List<Member> list = memberMapper.selectByExample(example);
        return list;
    }

    //注册
    public void register(MemberRegisterReq req){
        String mobile = req.getMobile();
        Date now = new Date();
        Member memberDB = selectByMember(mobile);
        if (memberDB != null){
            throw new BusinessException(BusinessExceptionEnum.MEMBER_MOBILE_HAD_REGISTER);
        }
        Member member = new Member();
        member.setId(IdUtil.getSnowflakeNextId());
        member.setMobile(mobile);
        member.setPassword(req.getPassword());
        member.setName(mobile.substring(0,3)+"****"+mobile.substring(7));
        member.setCreatedAt(now);
        member.setUpdatedAt(now);

        memberMapper.insert(member);
    }

    //重置密码
    public void reset(MemberResetReq req){
        String mobile = req.getMobile();
        Date now = new Date();
        Member memberDB = selectByMember(mobile);
        if (memberDB == null){
            throw new BusinessException(BusinessExceptionEnum.MEMBER_MOBILE_NOT_REGISTER);
        }
        Member member = new Member();
        member.setId(memberDB.getId());
        member.setPassword(req.getPassword());
        member.setCreatedAt(now);
        memberMapper.updateByPrimaryKeySelective(member);
    }

    //登录
    public MemberLoginResp login(MemberLoginReq req){
        Member memberDB = selectByMember(req.getMobile());
        if (memberDB == null){
            log.warn("手机号不存在,{}",req.getMobile());
            throw new BusinessException(BusinessExceptionEnum.MEMBER_LOGIN_ERROR);
        }
        if (memberDB.getPassword().equalsIgnoreCase(req.getPassword())){
            log.info("登录成功,{}",req.getMobile());
            MemberLoginResp memberLoginResp = new MemberLoginResp();
            memberLoginResp.setName(memberDB.getName());
            memberLoginResp.setId(memberDB.getId());

            Map<String, Object> map = BeanUtil.beanToMap(memberLoginResp);

            String token = JwtUtil.createLoginToken(map);
            memberLoginResp.setToken(token);

            memberLoginLogService.save(memberLoginResp);

            return memberLoginResp;
        }else {
            log.warn("密码错误,{}",req.getMobile());
            throw new BusinessException(BusinessExceptionEnum.MEMBER_LOGIN_ERROR);
        }
    }

    public StatisticResp getUserCount() {
        StatisticResp statisticResp = new StatisticResp();

        MemberExample example = new MemberExample();
        long userCount = memberMapper.countByExample(example);

        statisticResp.setTotalCount(userCount);
        return statisticResp;
    }

    //今日注册人数
    public StatisticResp getRegisterUserCount() {
        StatisticResp statisticResp = new StatisticResp();

        MemberExample example = new MemberExample();
        example.createCriteria().andCreatedAtGreaterThanOrEqualTo(LocalDate.now().atStartOfDay())      // 今天 0 点之后
                .andCreatedAtLessThan(LocalDate.now().plusDays(1).atStartOfDay());    // 明天 0 点之前

        long todayNewUsers = memberMapper.countByExample(example);

        statisticResp.setTodayNewUsers(todayNewUsers);
        return statisticResp;
    }
}