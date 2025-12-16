package com.jiawa.lyw.service;


import com.jiawa.lyw.mapper.ReportMapperCust;
import com.jiawa.lyw.resp.StatisticResp;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class ReportService {
    @Resource
    private ReportMapperCust reportMapperCust;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public StatisticResp queryStatistic(){
        StatisticResp resp = new StatisticResp();
        resp.setOnlineCount(reportMapperCust.queryOnlineCount());
        return resp;
    }

    public StatisticResp getDau(){
        StatisticResp resp = new StatisticResp();
         String date = LocalDate.now().toString();

        String key = "dau:" + date;
        Long dau = stringRedisTemplate.opsForSet().size(key);
        resp.setDau(dau);

        return resp;
    }
}
