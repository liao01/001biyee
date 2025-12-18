package com.jiawa.lyw.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.IdUtil;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.jiawa.lyw.Util.JwtUtil;
import com.jiawa.lyw.domain.User;
import com.jiawa.lyw.domain.UserExample;
import com.jiawa.lyw.exception.BusinessException;
import com.jiawa.lyw.exception.BusinessExceptionEnum;
import com.jiawa.lyw.mapper.UserMapper;
import com.jiawa.lyw.req.PageReq;
import com.jiawa.lyw.req.UserDeleteReq;
import com.jiawa.lyw.req.UserLoginReq;
import com.jiawa.lyw.req.UserRegisterReq;
import com.jiawa.lyw.resp.PageResp;
import com.jiawa.lyw.resp.UserLoginResp;
import com.jiawa.lyw.resp.UserResp;
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

    public PageResp<UserResp> selectUser(PageReq req){
        UserExample userExample = new UserExample();

        PageHelper.startPage(req.getPage(),req.getSize());//分页必须要和查询放在一起
        List<User> users = userMapper.selectByExample(userExample);
        //构造分页的返回信息
        PageResp<UserResp> pageResp = new PageResp<>();
        //获取分页的信息，需要获取总数
        PageInfo<User> pageInfo = new PageInfo<>(users);
        pageResp.setTotal(pageInfo.getTotal());
        //获取当前分页列表的内容
        List<UserResp> list = BeanUtil.copyToList(users, UserResp.class);
        pageResp.setPage(list);

        return  pageResp;
    }

    public void register(UserRegisterReq req){
        UserExample userExample = new UserExample();
        UserExample.Criteria criteria = userExample.createCriteria();
        criteria.andLoginNameEqualTo(req.getLoginName());

        if (!userMapper.selectByExample(userExample).isEmpty()) {
            log.warn("用户已经拥有,{}",req.getLoginName());
            throw new BusinessException(BusinessExceptionEnum.User_MOBILE_HAD_REGISTER);
        }

        User user = new User();
        user.setId(IdUtil.getSnowflakeNextId());
        user.setLoginName(req.getLoginName());
        user.setPassword(req.getPassword());

        userMapper.insert(user);
    }

    public void delete(UserDeleteReq req) {
        UserExample userExample = new UserExample();
        UserExample.Criteria criteria = userExample.createCriteria();
        criteria.andIdEqualTo(Long.parseLong(req.getId())); // 确保是数字

        int deletedCount = userMapper.deleteByExample(userExample);
        if (deletedCount == 0) {
            log.warn("用户不存在, {}", req.getId());
            throw new BusinessException(BusinessExceptionEnum.User_MOBILE_HAD_HAVE);
        }
    }
}