package com.openmanus.saa.service.session.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.openmanus.saa.service.session.entity.SessionStateEntity;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Select;

/**
 * 会话状态 Mapper
 */
public interface SessionStateMapper extends BaseMapper<SessionStateEntity> {

    @Select("SELECT * FROM session_state WHERE updated_at < #{threshold}")
    List<SessionStateEntity> findExpired(Instant threshold);

    @Delete("DELETE FROM session_state WHERE updated_at < #{threshold}")
    int deleteExpired(Instant threshold);
}
