package com.jiawa.lyw.service;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.IdUtil;
import com.jiawa.lyw.context.LoginMemberContext;
import com.jiawa.lyw.domain.MemberLoginLog;
import com.jiawa.lyw.domain.MemberLoginLogExample;
import com.jiawa.lyw.mapper.MemberLoginLogMapper;
import com.jiawa.lyw.resp.MemberLoginResp;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Slf4j
@Service
public class MemberLoginLogService {

    @Resource
    private MemberLoginLogMapper memberLoginLogMapper;

    public void  save(MemberLoginResp memberLoginResp) {
        log.info("记录用户登录，会员id:{}", memberLoginResp.getId());
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

    public void upadteHeartInfo(){
        MemberLoginResp member = LoginMemberContext.getMember();
        String token = member.getToken();
        log.info("更新会员心跳，会员id:{}", member.getId());
        MemberLoginLogExample example = new MemberLoginLogExample();
        example.createCriteria().andTokenEqualTo(token);
        example.setOrderByClause("id desc");

        List<MemberLoginLog> memberLoginLogs = memberLoginLogMapper.selectByExample(example);

        if (CollUtil.isEmpty(memberLoginLogs)) {
            log.warn("未找到会员登录信息，会员id:{}", member.getId());
            save(member);
            return;
        }

        MemberLoginLog memberLoginLogDB = memberLoginLogs.get(0);

        memberLoginLogDB.setHeartCount(memberLoginLogDB.getHeartCount() + 1);
        memberLoginLogDB.setLastHeartTime(new Date());

        memberLoginLogMapper.updateByPrimaryKeySelective(memberLoginLogDB);
    }
}
