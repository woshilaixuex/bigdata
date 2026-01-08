package com.sales.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableCaching
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // 使用Jackson2JsonRedisSerializer来序列化和反序列化redis的value值
        Jackson2JsonRedisSerializer<Object> jackson2JsonRedisSerializer = new Jackson2JsonRedisSerializer<>(Object.class);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        objectMapper.activateDefaultTyping(LaissezFaireSubTypeValidator.instance, ObjectMapper.DefaultTyping.NON_FINAL);
        objectMapper.registerModule(new JavaTimeModule());
        jackson2JsonRedisSerializer.setObjectMapper(objectMapper);

        // 使用StringRedisSerializer来序列化和反序列化redis的key值
        StringRedisSerializer stringRedisSerializer = new StringRedisSerializer();

        // 设置key和value的序列化规则
        template.setKeySerializer(stringRedisSerializer);
        template.setValueSerializer(jackson2JsonRedisSerializer);
        template.setHashKeySerializer(stringRedisSerializer);
        template.setHashValueSerializer(jackson2JsonRedisSerializer);

        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(30))
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(jackson2JsonRedisSerializer()))
                .disableCachingNullValues();

        // 针对不同缓存设置不同的过期时间
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();
        
        // 商品缓存5分钟
        cacheConfigurations.put("product", defaultConfig.entryTtl(Duration.ofMinutes(5)));
        
        // 用户缓存30分钟
        cacheConfigurations.put("user", defaultConfig.entryTtl(Duration.ofMinutes(30)));
        
        // 订单缓存1小时
        cacheConfigurations.put("order", defaultConfig.entryTtl(Duration.ofHours(1)));
        
        // 销售数据缓存10分钟
        cacheConfigurations.put("sales", defaultConfig.entryTtl(Duration.ofMinutes(10)));
        
        // 购物车缓存7天
        cacheConfigurations.put("cart", defaultConfig.entryTtl(Duration.ofDays(7)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .build();
    }

    private Jackson2JsonRedisSerializer<Object> jackson2JsonRedisSerializer() {
        Jackson2JsonRedisSerializer<Object> jackson2JsonRedisSerializer = new Jackson2JsonRedisSerializer<>(Object.class);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        objectMapper.activateDefaultTyping(LaissezFaireSubTypeValidator.instance, ObjectMapper.DefaultTyping.NON_FINAL);
        objectMapper.registerModule(new JavaTimeModule());
        jackson2JsonRedisSerializer.setObjectMapper(objectMapper);
        return jackson2JsonRedisSerializer;
    }

    // Redis Key 前缀常量
    public static class RedisKeys {
        // 商品库存
        public static final String STOCK_PREFIX = "stock:";
        public static final String SECKILL_STOCK_PREFIX = "seckill_stock:";
        
        // 购物车
        public static final String CART_PREFIX = "cart:";
        
        // 销售排行榜
        public static final String RANK_DAILY_SALE = "rank:daily:sale";
        public static final String RANK_WEEKLY_SALE = "rank:weekly:sale";
        public static final String RANK_MONTHLY_SALE = "rank:monthly:sale";
        
        // 用户会话
        public static final String SESSION_PREFIX = "session:";
        public static final String TOKEN_PREFIX = "token:";
        public static final String ONLINE_USERS = "online:users";
        
        // 实时销售看板
        public static final String DASHBOARD_PREFIX = "dashboard:";
        public static final String STAT_ORDERS_TODAY = "stat:orders:today";
        public static final String STAT_SALES_TODAY = "stat:sales:today";
        public static final String HOT_PRODUCTS = "hot:products";

        // 订单状态实时缓存
        public static final String ORDER_STATUS_PREFIX = "order:status:";
        public static final String ORDER_STATS_PREFIX = "order:stats:";
        
        // 限流与计数器
        public static final String LIMIT_PREFIX = "limit:";
        public static final String VIEW_COUNT_PREFIX = "view_count:product:";
        public static final String LOCK_PREFIX = "lock:";
        
        // 商品信息缓存
        public static final String PRODUCT_CACHE_PREFIX = "product:cache:";
        public static final String CATEGORY_TREE = "category:tree";
        
        // 消息队列
        public static final String QUEUE_ORDER_PROCESS = "queue:order:process";
        public static final String QUEUE_STOCK_DEDUCT = "queue:stock:deduct";
        public static final String QUEUE_STOCK_SYNC = "queue:stock:sync";
    }
}
