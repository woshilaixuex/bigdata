package com.sales.service;

import com.sales.config.RedisConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class RedisService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    // =============================String=============================

    public void set(String key, Object value) {
        try {
            redisTemplate.opsForValue().set(key, value);
            log.debug("Redis set: {} = {}", key, value);
        } catch (Exception e) {
            log.error("Redis set error: key={}, value={}", key, value, e);
        }
    }

    public void set(String key, Object value, long timeout, TimeUnit unit) {
        try {
            redisTemplate.opsForValue().set(key, value, timeout, unit);
            log.debug("Redis set with timeout: {} = {}, timeout={} {}", key, value, timeout, unit);
        } catch (Exception e) {
            log.error("Redis set with timeout error: key={}, value={}, timeout={} {}", key, value, timeout, unit, e);
        }
    }

    public boolean setIfAbsent(String key, Object value, long timeout, TimeUnit unit) {
        try {
            Boolean result = redisTemplate.opsForValue().setIfAbsent(key, value, timeout, unit);
            log.debug("Redis setIfAbsent: {} = {}, timeout={} {}, result={}", key, value, timeout, unit, result);
            return result != null && result;
        } catch (Exception e) {
            log.error("Redis setIfAbsent error: key={}, value={}, timeout={} {}", key, value, timeout, unit, e);
            return false;
        }
    }

    public Object get(String key) {
        try {
            Object value = redisTemplate.opsForValue().get(key);
            log.debug("Redis get: {} = {}", key, value);
            return value;
        } catch (Exception e) {
            log.error("Redis get error: key={}", key, e);
            return null;
        }
    }

    public boolean del(String key) {
        try {
            Boolean result = redisTemplate.delete(key);
            log.debug("Redis delete: {} = {}", key, result);
            return result != null && result;
        } catch (Exception e) {
            log.error("Redis delete error: key={}", key, e);
            return false;
        }
    }

    public boolean del(String... keys) {
        try {
            Long count = redisTemplate.delete(Arrays.asList(keys));
            log.debug("Redis delete multiple: count={}", count);
            return count != null && count > 0;
        } catch (Exception e) {
            log.error("Failed to delete multiple keys: {}", Arrays.toString(keys), e);
            return false;
        }
    }

    public boolean exists(String key) {
        try {
            Boolean result = redisTemplate.hasKey(key);
            log.debug("Redis exists: {} = {}", key, result);
            return result != null && result;
        } catch (Exception e) {
            log.error("Redis exists error: key={}", key, e);
            return false;
        }
    }

    public boolean expire(String key, long timeout, TimeUnit unit) {
        try {
            Boolean result = redisTemplate.expire(key, timeout, unit);
            log.debug("Redis expire: {} = {} {}", key, timeout, unit);
            return result != null && result;
        } catch (Exception e) {
            log.error("Redis expire error: key={}, timeout={} {}", key, timeout, unit, e);
            return false;
        }
    }

    public long getExpire(String key) {
        try {
            Long result = redisTemplate.getExpire(key);
            log.debug("Redis getExpire: {} = {}", key, result);
            return result != null ? result : -1;
        } catch (Exception e) {
            log.error("Redis getExpire error: key={}", key, e);
            return -1;
        }
    }

    public long incr(String key) {
        try {
            Long result = redisTemplate.opsForValue().increment(key);
            log.debug("Redis incr: {} = {}", key, result);
            return result != null ? result : 0;
        } catch (Exception e) {
            log.error("Redis incr error: key={}", key, e);
            return 0;
        }
    }

    public long incr(String key, long delta) {
        try {
            Long result = redisTemplate.opsForValue().increment(key, delta);
            log.debug("Redis incr: {} by {} = {}", key, delta, result);
            return result != null ? result : 0;
        } catch (Exception e) {
            log.error("Redis incr error: key={}, delta={}", key, delta, e);
            return 0;
        }
    }

    public double incrByFloat(String key, double delta) {
        try {
            Double result = redisTemplate.opsForValue().increment(key, delta);
            log.debug("Redis incrByFloat: {} by {} = {}", key, delta, result);
            return result != null ? result : 0.0;
        } catch (Exception e) {
            log.error("Redis incrByFloat error: key={}, delta={}", key, delta, e);
            return 0.0;
        }
    }

    public long decr(String key) {
        try {
            Long result = redisTemplate.opsForValue().decrement(key);
            log.debug("Redis decr: {} = {}", key, result);
            return result != null ? result : 0;
        } catch (Exception e) {
            log.error("Redis decr error: key={}", key, e);
            return 0;
        }
    }

    public long decr(String key, long delta) {
        try {
            Long result = redisTemplate.opsForValue().decrement(key, delta);
            log.debug("Redis decr: {} by {} = {}", key, delta, result);
            return result != null ? result : 0;
        } catch (Exception e) {
            log.error("Redis decr error: key={}, delta={}", key, delta, e);
            return 0;
        }
    }

    // =============================Hash=============================

    public void hset(String key, String field, Object value) {
        try {
            redisTemplate.opsForHash().put(key, field, value);
            log.debug("Redis hset: {} {} = {}", key, field, value);
        } catch (Exception e) {
            log.error("Redis hset error: key={}, field={}, value={}", key, field, value, e);
        }
    }

    public void hset(String key, Map<String, Object> map) {
        try {
            redisTemplate.opsForHash().putAll(key, map);
            log.debug("Redis hset all: {} = {}", key, map);
        } catch (Exception e) {
            log.error("Redis hset all error: key={}, map={}", key, map, e);
        }
    }

    public Object hget(String key, String field) {
        try {
            Object value = redisTemplate.opsForHash().get(key, field);
            log.debug("Redis hget: {} {} = {}", key, field, value);
            return value;
        } catch (Exception e) {
            log.error("Redis hget error: key={}, field={}", key, field, e);
            return null;
        }
    }

    public Map<Object, Object> hgetAll(String key) {
        try {
            Map<Object, Object> map = redisTemplate.opsForHash().entries(key);
            log.debug("Redis hgetAll: {} = {}", key, map);
            return map;
        } catch (Exception e) {
            log.error("Redis hgetAll error: key={}", key, e);
            return null;
        }
    }

    public boolean hdel(String key, String... fields) {
        try {
            Long result = redisTemplate.opsForHash().delete(key, fields);
            log.debug("Redis hdel: {} {} = {}", key, fields, result);
            return result != null && result > 0;
        } catch (Exception e) {
            log.error("Redis hdel error: key={}, fields={}", key, fields, e);
            return false;
        }
    }

    public boolean hexists(String key, String field) {
        try {
            Boolean result = redisTemplate.opsForHash().hasKey(key, field);
            log.debug("Redis hexists: {} {} = {}", key, field, result);
            return result != null && result;
        } catch (Exception e) {
            log.error("Redis hexists error: key={}, field={}", key, field, e);
            return false;
        }
    }

    public long hincrBy(String key, String field, long delta) {
        try {
            Long result = redisTemplate.opsForHash().increment(key, field, delta);
            log.debug("Redis hincrBy: {} {} by {} = {}", key, field, delta, result);
            return result != null ? result : 0;
        } catch (Exception e) {
            log.error("Redis hincrBy error: key={}, field={}, delta={}", key, field, delta, e);
            return 0;
        }
    }

    public double hincrByFloat(String key, String field, double delta) {
        try {
            Double result = redisTemplate.opsForHash().increment(key, field, delta);
            log.debug("Redis hincrByFloat: {} {} by {} = {}", key, field, delta, result);
            return result != null ? result : 0.0;
        } catch (Exception e) {
            log.error("Redis hincrByFloat error: key={}, field={}, delta={}", key, field, delta, e);
            return 0.0;
        }
    }

    // =============================List=============================

    public long lpush(String key, Object... values) {
        try {
            Long result = redisTemplate.opsForList().leftPushAll(key, values);
            log.debug("Redis lpush: {} = {}", key, result);
            return result != null ? result : 0;
        } catch (Exception e) {
            log.error("Redis lpush error: key={}, values={}", key, values, e);
            return 0;
        }
    }

    public long rpush(String key, Object... values) {
        try {
            Long result = redisTemplate.opsForList().rightPushAll(key, values);
            log.debug("Redis rpush: {} = {}", key, result);
            return result != null ? result : 0;
        } catch (Exception e) {
            log.error("Redis rpush error: key={}, values={}", key, values, e);
            return 0;
        }
    }

    public Object lpop(String key) {
        try {
            Object value = redisTemplate.opsForList().leftPop(key);
            log.debug("Redis lpop: {} = {}", key, value);
            return value;
        } catch (Exception e) {
            log.error("Redis lpop error: key={}", key, e);
            return null;
        }
    }

    public Object rpop(String key) {
        try {
            Object value = redisTemplate.opsForList().rightPop(key);
            log.debug("Redis rpop: {} = {}", key, value);
            return value;
        } catch (Exception e) {
            log.error("Redis rpop error: key={}", key, e);
            return null;
        }
    }

    public List<Object> lrange(String key, long start, long end) {
        try {
            List<Object> list = redisTemplate.opsForList().range(key, start, end);
            log.debug("Redis lrange: {} {} {} = {}", key, start, end, list);
            return list;
        } catch (Exception e) {
            log.error("Redis lrange error: key={}, start={}, end={}", key, start, end, e);
            return null;
        }
    }

    public long llen(String key) {
        try {
            Long result = redisTemplate.opsForList().size(key);
            log.debug("Redis llen: {} = {}", key, result);
            return result != null ? result : 0;
        } catch (Exception e) {
            log.error("Redis llen error: key={}", key, e);
            return 0;
        }
    }

    // =============================Set=============================

    public long sadd(String key, Object... values) {
        try {
            Long result = redisTemplate.opsForSet().add(key, values);
            log.debug("Redis sadd: {} = {}", key, result);
            return result != null ? result : 0;
        } catch (Exception e) {
            log.error("Redis sadd error: key={}, values={}", key, values, e);
            return 0;
        }
    }

    public Set<Object> smembers(String key) {
        try {
            Set<Object> set = redisTemplate.opsForSet().members(key);
            log.debug("Redis smembers: {} = {}", key, set);
            return set;
        } catch (Exception e) {
            log.error("Redis smembers error: key={}", key, e);
            return null;
        }
    }

    public boolean sismember(String key, Object value) {
        try {
            Boolean result = redisTemplate.opsForSet().isMember(key, value);
            log.debug("Redis sismember: {} {} = {}", key, value, result);
            return result != null && result;
        } catch (Exception e) {
            log.error("Redis sismember error: key={}, value={}", key, value, e);
            return false;
        }
    }

    public long srem(String key, Object... values) {
        try {
            Long result = redisTemplate.opsForSet().remove(key, values);
            log.debug("Redis srem: {} = {}", key, result);
            return result != null ? result : 0;
        } catch (Exception e) {
            log.error("Redis srem error: key={}, values={}", key, values, e);
            return 0;
        }
    }

    // =============================Sorted Set=============================

    public boolean zadd(String key, double score, Object value) {
        try {
            Boolean result = redisTemplate.opsForZSet().add(key, value, score);
            log.debug("Redis zadd: {} {} {} = {}", key, score, value, result);
            return result != null && result;
        } catch (Exception e) {
            log.error("Redis zadd error: key={}, score={}, value={}", key, score, value, e);
            return false;
        }
    }

    public Double zincrby(String key, double delta, Object value) {
        try {
            Double result = redisTemplate.opsForZSet().incrementScore(key, value, delta);
            log.debug("Redis zincrby: {} {} {} = {}", key, delta, value, result);
            return result;
        } catch (Exception e) {
            log.error("Redis zincrby error: key={}, delta={}, value={}", key, delta, value, e);
            return null;
        }
    }

    public Long zrank(String key, Object value) {
        try {
            Long result = redisTemplate.opsForZSet().rank(key, value);
            log.debug("Redis zrank: {} {} = {}", key, value, result);
            return result;
        } catch (Exception e) {
            log.error("Redis zrank error: key={}, value={}", key, value, e);
            return null;
        }
    }

    public Long zrevrank(String key, Object value) {
        try {
            Long result = redisTemplate.opsForZSet().reverseRank(key, value);
            log.debug("Redis zrevrank: {} {} = {}", key, value, result);
            return result;
        } catch (Exception e) {
            log.error("Redis zrevrank error: key={}, value={}", key, value, e);
            return null;
        }
    }

    public Double zscore(String key, Object value) {
        try {
            Double result = redisTemplate.opsForZSet().score(key, value);
            log.debug("Redis zscore: {} {} = {}", key, value, result);
            return result;
        } catch (Exception e) {
            log.error("Redis zscore error: key={}, value={}", key, value, e);
            return null;
        }
    }

    public Long zcard(String key) {
        try {
            Long result = redisTemplate.opsForZSet().size(key);
            log.debug("Redis zcard: {} = {}", key, result);
            return result;
        } catch (Exception e) {
            log.error("Redis zcard error: key={}", key, e);
            return null;
        }
    }

    public Set<Object> zrange(String key, long start, long end) {
        try {
            Set<Object> set = redisTemplate.opsForZSet().range(key, start, end);
            log.debug("Redis zrange: {} {} {} = {}", key, start, end, set);
            return set;
        } catch (Exception e) {
            log.error("Redis zrange error: key={}, start={}, end={}", key, start, end, e);
            return null;
        }
    }

    public Set<Object> zrevrange(String key, long start, long end) {
        try {
            Set<Object> set = redisTemplate.opsForZSet().reverseRange(key, start, end);
            log.debug("Redis zrevrange: {} {} {} = {}", key, start, end, set);
            return set;
        } catch (Exception e) {
            log.error("Redis zrevrange error: key={}, start={}, end={}", key, start, end, e);
            return null;
        }
    }

    public Set<Object> zrevrangeByScore(String key, double max, double min) {
        try {
            Set<Object> set = redisTemplate.opsForZSet().reverseRangeByScore(key, max, min);
            log.debug("Redis zrevrangeByScore: {} {} {} = {}", key, max, min, set);
            return set;
        } catch (Exception e) {
            log.error("Redis zrevrangeByScore error: key={}, max={}, min={}", key, max, min, e);
            return null;
        }
    }

    public long zrem(String key, Object... values) {
        try {
            Long result = redisTemplate.opsForZSet().remove(key, values);
            log.debug("Redis zrem: {} = {}", key, result);
            return result != null ? result : 0;
        } catch (Exception e) {
            log.error("Redis zrem error: key={}, values={}", key, values, e);
            return 0;
        }
    }

    // =============================通用方法=============================

    public Collection<String> keys(String pattern) {
        try {
            Set<String> keys = redisTemplate.keys(pattern);
            log.debug("Redis keys: {} = {}", pattern, keys);
            return keys;
        } catch (Exception e) {
            log.error("Redis keys error: pattern={}", pattern, e);
            return null;
        }
    }

    public void flushDb() {
        try {
            redisTemplate.getConnectionFactory().getConnection().flushDb();
            log.info("Redis flushDb executed");
        } catch (Exception e) {
            log.error("Redis flushDb error", e);
        }
    }

    public void flushAll() {
        try {
            redisTemplate.getConnectionFactory().getConnection().flushAll();
            log.info("Redis flushAll executed");
        } catch (Exception e) {
            log.error("Redis flushAll error", e);
        }
    }
}
