package io.github.majiajustar.codex;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 仅应用于已识别过载响应的指数退避策略。
 *
 * @param maxAttempts 包含首次请求在内的总尝试次数
 * @param initialDelay 首次重试前的等待时间
 * @param maxDelay 每次计算所得延迟的上限
 * @param multiplier 指数增长倍数，最小为 {@code 1.0}
 * @param jitterRatio 随机抖动比例，范围为 {@code 0.0} 到 {@code 1.0}
 */
public record RetryPolicy(
        int maxAttempts,
        Duration initialDelay,
        Duration maxDelay,
        double multiplier,
        double jitterRatio) {

    public RetryPolicy {
        if (maxAttempts < 1) throw new IllegalArgumentException("maxAttempts must be at least 1");
        if (initialDelay.isNegative()) throw new IllegalArgumentException("initialDelay must not be negative");
        if (maxDelay.isNegative()) throw new IllegalArgumentException("maxDelay must not be negative");
        if (multiplier < 1.0) throw new IllegalArgumentException("multiplier must be at least 1.0");
        if (jitterRatio < 0.0 || jitterRatio > 1.0) {
            throw new IllegalArgumentException("jitterRatio must be between 0.0 and 1.0");
        }
    }

    /** 返回 SDK 默认策略：最多尝试三次，并采用带上限和随机抖动的指数退避。 */
    public static RetryPolicy overloadDefaults() {
        return new RetryPolicy(
                3,
                Duration.ofMillis(250),
                Duration.ofSeconds(2),
                2.0,
                0.2);
    }

    /** 返回只尝试一次且不重试的策略。 */
    public static RetryPolicy disabled() {
        return new RetryPolicy(
                1,
                Duration.ZERO,
                Duration.ZERO,
                1.0,
                0.0);
    }

    Duration delayAfterAttempt(int attempt) {
        var scaledMillis = initialDelay.toMillis() * Math.pow(multiplier, attempt - 1);
        var cappedMillis = Math.min(maxDelay.toMillis(), scaledMillis);
        var jitterMillis = cappedMillis * jitterRatio;
        var randomizedMillis = jitterMillis == 0.0
                ? cappedMillis
                : cappedMillis + ThreadLocalRandom.current().nextDouble(-jitterMillis, jitterMillis);
        return Duration.ofMillis(Math.max(0L, Math.round(randomizedMillis)));
    }
}
