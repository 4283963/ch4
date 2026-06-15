package com.garage.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.*;

@Service
public class GarageRotationLockService {

    private static final Logger log = LoggerFactory.getLogger(GarageRotationLockService.class);

    private final StringRedisTemplate redisTemplate;

    @Value("${garage.lock.rotation-key:garage:lock:rotation}")
    private String rotationLockKey;

    @Value("${garage.lock.queue-key:garage:queue:rotation}")
    private String rotationQueueKey;

    @Value("${garage.lock.wait-timeout-seconds:120}")
    private int waitTimeoutSeconds;

    @Value("${garage.lock.lock-timeout-seconds:300}")
    private int lockTimeoutSeconds;

    private final ConcurrentHashMap<String, CompletableFuture<String>> pendingRequests = new ConcurrentHashMap<>();
    private final ScheduledExecutorService lockRefresher = Executors.newSingleThreadScheduledExecutor();
    private final ConcurrentHashMap<String, ScheduledFuture<?>> refreshTasks = new ConcurrentHashMap<>();

    private static final String ACQUIRE_LOCK_SCRIPT =
            "if redis.call('exists', KEYS[1]) == 0 then " +
            "  redis.call('hset', KEYS[1], 'holder', ARGV[1], 'timestamp', ARGV[2], 'plate', ARGV[3]) " +
            "  redis.call('expire', KEYS[1], ARGV[4]) " +
            "  redis.call('lrem', KEYS[2], 0, ARGV[1]) " +
            "  return 1 " +
            "else " +
            "  if redis.call('hget', KEYS[1], 'holder') == ARGV[1] then " +
            "    redis.call('expire', KEYS[1], ARGV[4]) " +
            "    return 1 " +
            "  end " +
            "  return 0 " +
            "end";

    private static final String RELEASE_LOCK_SCRIPT =
            "if redis.call('hget', KEYS[1], 'holder') == ARGV[1] then " +
            "  redis.call('del', KEYS[1]) " +
            "  return 1 " +
            "else " +
            "  return 0 " +
            "end";

    private static final String FORCE_ACQUIRE_SCRIPT =
            "redis.call('del', KEYS[1]) " +
            "redis.call('hset', KEYS[1], 'holder', ARGV[1], 'timestamp', ARGV[2], 'plate', ARGV[3]) " +
            "redis.call('expire', KEYS[1], ARGV[4]) " +
            "redis.call('lrem', KEYS[2], 0, ARGV[1]) " +
            "return 1";

    @Autowired
    public GarageRotationLockService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public static class LockResult {
        private final boolean acquired;
        private final String requestId;
        private final int queuePosition;
        private final String currentHolder;

        public LockResult(boolean acquired, String requestId, int queuePosition, String currentHolder) {
            this.acquired = acquired;
            this.requestId = requestId;
            this.queuePosition = queuePosition;
            this.currentHolder = currentHolder;
        }

        public boolean isAcquired() { return acquired; }
        public String getRequestId() { return requestId; }
        public int getQueuePosition() { return queuePosition; }
        public String getCurrentHolder() { return currentHolder; }
    }

    public LockResult acquireLockOrEnqueue(String licensePlate) {
        String requestId = UUID.randomUUID().toString();
        String timestamp = String.valueOf(System.currentTimeMillis());

        DefaultRedisScript<Long> script = new DefaultRedisScript<>(ACQUIRE_LOCK_SCRIPT, Long.class);
        Long result = redisTemplate.execute(
                script,
                java.util.List.of(rotationLockKey, rotationQueueKey),
                requestId, timestamp, licensePlate, String.valueOf(lockTimeoutSeconds)
        );

        if (result != null && result == 1) {
            log.info("Lock acquired immediately for request={}, plate={}", requestId, licensePlate);
            startLockRefresh(requestId);
            return new LockResult(true, requestId, 0, requestId);
        }

        Long queueSize = redisTemplate.opsForList().rightPush(rotationQueueKey, requestId + ":" + licensePlate);
        String holder = (String) redisTemplate.opsForHash().get(rotationLockKey, "plate");
        int position = queueSize != null ? queueSize.intValue() : 0;

        log.info("Lock busy, enqueued request={}, plate={}, position={}, currentHolder={}",
                requestId, licensePlate, position, holder);

        return new LockResult(false, requestId, position, holder != null ? holder : "unknown");
    }

    public boolean tryAcquireLock(String requestId, String licensePlate) {
        String timestamp = String.valueOf(System.currentTimeMillis());

        DefaultRedisScript<Long> script = new DefaultRedisScript<>(ACQUIRE_LOCK_SCRIPT, Long.class);
        Long result = redisTemplate.execute(
                script,
                java.util.List.of(rotationLockKey, rotationQueueKey),
                requestId, timestamp, licensePlate, String.valueOf(lockTimeoutSeconds)
        );

        if (result != null && result == 1) {
            log.info("Lock acquired for queued request={}, plate={}", requestId, licensePlate);
            startLockRefresh(requestId);
            return true;
        }
        return false;
    }

    public void releaseLock(String requestId) {
        stopLockRefresh(requestId);

        DefaultRedisScript<Long> script = new DefaultRedisScript<>(RELEASE_LOCK_SCRIPT, Long.class);
        Long result = redisTemplate.execute(
                script,
                Collections.singletonList(rotationLockKey),
                requestId
        );

        if (result != null && result == 1) {
            log.info("Lock released for request={}", requestId);
        } else {
            log.warn("Lock release failed for request={} (not holder or already expired)", requestId);
        }
    }

    public String pollQueue() {
        String entry = redisTemplate.opsForList().leftPop(rotationQueueKey);
        if (entry != null) {
            String[] parts = entry.split(":", 2);
            String nextRequestId = parts[0];
            String nextPlate = parts.length > 1 ? parts[1] : "unknown";

            String timestamp = String.valueOf(System.currentTimeMillis());

            DefaultRedisScript<Long> script = new DefaultRedisScript<>(FORCE_ACQUIRE_SCRIPT, Long.class);
            redisTemplate.execute(
                    script,
                    java.util.List.of(rotationLockKey, rotationQueueKey),
                    nextRequestId, timestamp, nextPlate, String.valueOf(lockTimeoutSeconds)
            );

            startLockRefresh(nextRequestId);
            log.info("Lock transferred to next in queue: request={}, plate={}", nextRequestId, nextPlate);
            return nextRequestId;
        }
        return null;
    }

    public int getQueueSize() {
        Long size = redisTemplate.opsForList().size(rotationQueueKey);
        return size != null ? size.intValue() : 0;
    }

    public String getCurrentHolder() {
        Object holder = redisTemplate.opsForHash().get(rotationLockKey, "plate");
        return holder != null ? holder.toString() : null;
    }

    public boolean isLocked() {
        return Boolean.TRUE.equals(redisTemplate.hasKey(rotationLockKey));
    }

    public int getQueuePosition(String requestId) {
        java.util.List<String> queue = redisTemplate.opsForList().range(rotationQueueKey, 0, -1);
        if (queue == null) return -1;
        for (int i = 0; i < queue.size(); i++) {
            if (queue.get(i).startsWith(requestId + ":")) {
                return i + 1;
            }
        }
        return -1;
    }

    private void startLockRefresh(String requestId) {
        ScheduledFuture<?> future = lockRefresher.scheduleAtFixedRate(() -> {
            try {
                String timestamp = String.valueOf(System.currentTimeMillis());
                DefaultRedisScript<Long> script = new DefaultRedisScript<>(ACQUIRE_LOCK_SCRIPT, Long.class);
                redisTemplate.execute(
                        script,
                        java.util.List.of(rotationLockKey, rotationQueueKey),
                        requestId, timestamp, "", String.valueOf(lockTimeoutSeconds)
                );
            } catch (Exception e) {
                log.error("Lock refresh failed for request={}", requestId, e);
            }
        }, lockTimeoutSeconds / 3, lockTimeoutSeconds / 3, TimeUnit.SECONDS);

        refreshTasks.put(requestId, future);
    }

    private void stopLockRefresh(String requestId) {
        ScheduledFuture<?> future = refreshTasks.remove(requestId);
        if (future != null) {
            future.cancel(false);
        }
    }

    public boolean forceReleaseAll() {
        redisTemplate.delete(rotationLockKey);
        redisTemplate.delete(rotationQueueKey);
        log.warn("Force released all locks and cleared queue");
        return true;
    }
}
