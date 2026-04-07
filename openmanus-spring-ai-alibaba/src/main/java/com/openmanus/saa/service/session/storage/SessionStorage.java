package com.openmanus.saa.service.session.storage;

import com.openmanus.saa.model.session.Session;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 会话存储策略接口
 *
 * <p>支持多种存储后端：内存、MySQL、Redis 等。
 * 通过配置 {@code openmanus.session.storage} 切换。
 */
public interface SessionStorage {

    /**
     * 保存会话
     *
     * @param session 会话状态
     * @return 保存后的会话
     */
    Session save(Session session);

    /**
     * 根据 ID 查找会话
     *
     * @param sessionId 会话 ID
     * @return 会话状态（如果存在）
     */
    Optional<Session> findById(String sessionId);

    /**
     * 查找所有会话
     *
     * @return 所有会话列表
     */
    List<Session> findAll();

    /**
     * 删除会话
     *
     * @param sessionId 会话 ID
     */
    void deleteById(String sessionId);

    /**
     * 查找过期的会话
     *
     * @param threshold 过期阈值（更新时间早于此时间的会话）
     * @return 过期会话列表
     */
    List<Session> findExpired(Instant threshold);

    /**
     * 统计会话数量
     *
     * @return 会话总数
     */
    long count();

    /**
     * 检查会话是否存在
     *
     * @param sessionId 会话 ID
     * @return 是否存在
     */
    default boolean existsById(String sessionId) {
        return findById(sessionId).isPresent();
    }
}
