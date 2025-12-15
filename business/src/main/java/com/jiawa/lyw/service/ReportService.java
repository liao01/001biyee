package com.jiawa.lyw.service;


import com.jiawa.lyw.mapper.ReportMapperCust;
import com.jiawa.lyw.resp.StatisticResp;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

@Service
public class ReportService {
    @Resource
    private ReportMapperCust reportMapperCust;

    public StatisticResp queryStatistic(){
        StatisticResp resp = new StatisticResp();
        resp.setOnlineCount(reportMapperCust.queryOnlineCount());
        return resp;
    }
}
