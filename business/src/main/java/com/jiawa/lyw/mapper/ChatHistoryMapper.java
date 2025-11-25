package com.jiawa.lyw.mapper;

import com.jiawa.lyw.domain.ChatHistory;
import com.jiawa.lyw.domain.ChatHistoryExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface ChatHistoryMapper {
    long countByExample(ChatHistoryExample example);

    int deleteByExample(ChatHistoryExample example);

    int deleteByPrimaryKey(Long id);

    int insert(ChatHistory record);

    int insertSelective(ChatHistory record);

    List<ChatHistory> selectByExampleWithBLOBs(ChatHistoryExample example);

    List<ChatHistory> selectByExample(ChatHistoryExample example);

    ChatHistory selectByPrimaryKey(Long id);

    int updateByExampleSelective(@Param("record") ChatHistory record, @Param("example") ChatHistoryExample example);

    int updateByExampleWithBLOBs(@Param("record") ChatHistory record, @Param("example") ChatHistoryExample example);

    int updateByExample(@Param("record") ChatHistory record, @Param("example") ChatHistoryExample example);

    int updateByPrimaryKeySelective(ChatHistory record);

    int updateByPrimaryKeyWithBLOBs(ChatHistory record);

    int updateByPrimaryKey(ChatHistory record);
}