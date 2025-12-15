package com.jiawa.lyw.service;

import cn.hutool.core.util.IdUtil;
import com.jiawa.lyw.domain.MemberLoginLog;
import com.jiawa.lyw.mapper.MemberLoginLogMapper;
import com.jiawa.lyw.resp.MemberLoginResp;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Date;

@Slf4j
@Service
public class MemberLoginLogService {

    @Resource
    private MemberLoginLogMapper memberLoginLogMapper;

    public void  save(MemberLoginResp memberLoginResp) {
        log.info("用户登录日志:{}", memberLoginResp);
        Date now = new Date();
        MemberLoginLog memberLoginLog = new MemberLoginLog();
        memberLoginLog.setId(IdUtil.getSnowflakeNextId());
        memberLoginLog.setMemberId(memberLoginResp.getId());
        memberLoginLog.setLoginTime(now);
        memberLoginLog.setToken(memberLoginResp.getToken());
        memberLoginLog.setHeartCount(0);
        memberLoginLog.setLastHeartTime(now);

        memberLoginLogMapper.insert(memberLoginLog);
    }
}
