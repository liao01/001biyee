package com.jiawa.lyw.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import com.jiawa.lyw.Util.JwtUtil;
import com.jiawa.lyw.domain.User;
import com.jiawa.lyw.domain.UserExample;
import com.jiawa.lyw.exception.BusinessException;
import com.jiawa.lyw.exception.BusinessExceptionEnum;
import com.jiawa.lyw.mapper.UserMapper;
import com.jiawa.lyw.req.MemberRegisterReq;
import com.jiawa.lyw.req.UserLoginReq;
import com.jiawa.lyw.resp.UserLoginResp;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class AdminService {
    @Autowired
    private UserMapper userMapper;

    public User selectByLoginName(String loginName){
        UserExample userExample = new UserExample();
        UserExample.Criteria criteria = userExample.createCriteria();
        criteria.andLoginNameEqualTo(loginName);
        List<User> list = userMapper.selectByExample(userExample);
        if(CollUtil.isNotEmpty(list)){
            return list.get(0);
        }else {
            return null;
        }
    }

//    public List<Member> findByMemberId(Long memberId){
//        MemberExample example = new MemberExample();
//        MemberExample.Criteria criteria = example.createCriteria();
//        criteria.andIdEqualTo(memberId);
//        List<Member> list = userMapper.selectByExample(example);
//        return list;
//    }

    //注册
    public void register(MemberRegisterReq req){
//        String mobile = req.getMobile();
//        Date now = new Date();
//        Member memberDB = selectByMember(mobile);
//        if (memberDB != null){
//            throw new BusinessException(BusinessExceptionEnum.MEMBER_MOBILE_HAD_REGISTER);
//        }
//        Member member = new Member();
//        member.setId(IdUtil.getSnowflakeNextId());
//        member.setMobile(mobile);
//        member.setPassword(req.getPassword());
//        member.setName(mobile.substring(0,3)+"****"+mobile.substring(7));
//        member.setCreatedAt(now);
//        member.setUpdatedAt(now);
//
//        userMapper.insert(member);
    }

    //重置密码
//    public void reset(MemberResetReq req){
//        String mobile = req.getMobile();
//        Date now = new Date();
//        Member memberDB = selectByMember(mobile);
//        if (memberDB == null){
//            throw new BusinessException(BusinessExceptionEnum.MEMBER_MOBILE_NOT_REGISTER);
//        }
//        Member member = new Member();
//        member.setId(memberDB.getId());
//        member.setPassword(req.getPassword());
//        member.setCreatedAt(now);
//        userMapper.updateByPrimaryKeySelective(member);
//    }

    /**
     *登录
     */
    public UserLoginResp login(UserLoginReq req){
        User userDB = selectByLoginName(req.getLoginName());
        if (userDB == null) {
            log.warn("登录名不存在,{}",req.getLoginName());
            throw new BusinessException(BusinessExceptionEnum.USER_LOGIN_ERROR);
        }
        if(userDB.getPassword().equalsIgnoreCase(req.getPassword())){
            log.info("登录成功",req.getLoginName());
            UserLoginResp resp = new UserLoginResp();
            resp.setLoginName(userDB.getLoginName());
            resp.setId(userDB.getId());

            //生成token
            Map<String, Object> map = BeanUtil.beanToMap(resp);
            String token = JwtUtil.createLoginToken(map);
            resp.setToken(token);

            return resp;

        }else{
            log.warn("密码错误,{}",req.getLoginName());
            throw new BusinessException(BusinessExceptionEnum.USER_LOGIN_ERROR);
        }
    }
}