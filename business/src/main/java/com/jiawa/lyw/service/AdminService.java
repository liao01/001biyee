package com.jiawa.lyw.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import com.jiawa.lyw.Util.JwtUtil;
import com.jiawa.lyw.domain.User;
import com.jiawa.lyw.domain.UserExample;
import com.jiawa.lyw.exception.BusinessException;
import com.jiawa.lyw.exception.BusinessExceptionEnum;
import com.jiawa.lyw.mapper.UserMapper;
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