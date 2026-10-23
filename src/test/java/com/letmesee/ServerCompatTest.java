package com.letmesee;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Folia 检测的离线回归：单测类路径上只有 paper-api，没有区域化服务端本体。
 * paper-api 里既有 {@code Bukkit#getRegionScheduler} 也有 {@code Entity#getScheduler}
 * （已用 javap 确认），所以 1.2.0 之前的「只看调度 API 是否存在」会在这里误判成
 * Folia——本测试锁住按服务端本体检测的正确行为。
 */
class ServerCompatTest {

    @Test
    void paperApiOnlyClasspathIsNotFolia() {
        assertFalse(ServerCompat.isFolia());
    }

    @Test
    void detectionIsStableAcrossCalls() {
        assertEquals(ServerCompat.isFolia(), ServerCompat.isFolia());
    }
}
