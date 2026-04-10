package com.openmanus.saa.service.session.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.openmanus.saa.service.session.entity.ConversationMessageEntity;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Select;

/**
 * 会话消息 Mapper
 */
public interface ConversationMessageMapper extends BaseMapper<ConversationMessageEntity> {

    @Select("SELECT * FROM conversation_message WHERE session_id = #{sessionId} ORDER BY seq_num")
    List<ConversationMessageEntity> findBySessionId(String sessionId);

    @Delete("DELETE FROM conversation_message WHERE session_id = #{sessionId}")
    int deleteBySessionId(String sessionId);
}
