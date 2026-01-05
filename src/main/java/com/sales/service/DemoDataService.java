package com.sales.service;

import com.sales.config.HBaseConfig;
import com.sales.config.RedisConfig;
import com.sales.entity.CartItem;
import com.sales.entity.Order;
import com.sales.entity.Product;
import com.sales.entity.SalesData;
import com.sales.entity.User;
import com.sales.repository.OrderRepository;
import com.sales.repository.ProductRepository;
import com.sales.repository.SalesDataRepository;
import com.sales.repository.UserRepository;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class DemoDataService {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SalesDataRepository salesDataRepository;

    @Autowired
    private StockService stockService;

    @Autowired
    private CartService cartService;

    @Autowired
    private RankingService rankingService;

    @Autowired
    private RedisService redisService;

    public InitResult initDemoData() throws IOException {
        InitResult result = new InitResult();

        result.productsInserted = initProducts();
        result.usersInserted = initUsers();
        result.salesDataInserted = initSalesData();
        result.ordersInserted = initOrders();

        initRedisAuxData();

        return result;
    }

    private int initProducts() throws IOException {
        List<Product> demoProducts = new ArrayList<>();
        demoProducts.add(buildProduct("P1001", "iPhone 15", "手机", "Apple", new BigDecimal("6999.00"), new BigDecimal("5200.00"), 120));
        demoProducts.add(buildProduct("P1002", "小米 14", "手机", "Xiaomi", new BigDecimal("3999.00"), new BigDecimal("3000.00"), 80));
        demoProducts.add(buildProduct("P1003", "华为 Mate 60", "手机", "Huawei", new BigDecimal("5999.00"), new BigDecimal("4500.00"), 50));
        demoProducts.add(buildProduct("P2001", "联想小新 Pro", "电脑", "Lenovo", new BigDecimal("5299.00"), new BigDecimal("4100.00"), 35));
        demoProducts.add(buildProduct("P2002", "MacBook Air", "电脑", "Apple", new BigDecimal("8999.00"), new BigDecimal("7000.00"), 20));
        demoProducts.add(buildProduct("P3001", "AirPods Pro", "耳机", "Apple", new BigDecimal("1899.00"), new BigDecimal("1200.00"), 100));
        demoProducts.add(buildProduct("P3002", "索尼 WH-1000XM5", "耳机", "Sony", new BigDecimal("2599.00"), new BigDecimal("1900.00"), 30));
        demoProducts.add(buildProduct("P4001", "小米手环 8", "穿戴", "Xiaomi", new BigDecimal("249.00"), new BigDecimal("120.00"), 200));
        demoProducts.add(buildProduct("P5001", "机械键盘", "外设", "Keychron", new BigDecimal("699.00"), new BigDecimal("420.00"), 60));
        demoProducts.add(buildProduct("P5002", "无线鼠标", "外设", "Logitech", new BigDecimal("399.00"), new BigDecimal("250.00"), 90));

        int inserted = 0;
        for (Product p : demoProducts) {
            if (!productRepository.existsById(p.getProductId())) {
                productRepository.save(p);
                inserted++;
            }

            int stock = p.getTotalStock() == null ? 0 : p.getTotalStock();
            if (!stockService.stockExists(p.getProductId())) {
                stockService.setStock(p.getProductId(), stock);
            }
        }

        return inserted;
    }

    private Product buildProduct(String productId, String name, String category, String brand, BigDecimal price, BigDecimal cost, int totalStock) {
        return Product.builder()
                .productId(productId)
                .name(name)
                .category(category)
                .brand(brand)
                .price(price)
                .cost(cost)
                .status(Product.Status.ON_SHELF.getCode())
                .createTime(LocalDateTime.now().minusDays(10))
                .description(name + " 官方正品")
                .spec("{}")
                .tags("demo")
                .totalStock(totalStock)
                .safeStock(Math.max(5, totalStock / 10))
                .lockStock(0)
                .viewCount(0L)
                .saleCount(0L)
                .collectCount(0L)
                .updateTime(LocalDateTime.now())
                .build();
    }

    private int initUsers() throws IOException {
        List<User> users = new ArrayList<>();
        users.add(buildUser("U001", "zhangsan", "张三"));
        users.add(buildUser("U002", "lisi", "李四"));
        users.add(buildUser("U003", "wangwu", "王五"));

        int inserted = 0;
        for (User u : users) {
            if (!userRepository.existsById(u.getUserId())) {
                userRepository.save(u);
                inserted++;
            }
        }
        return inserted;
    }

    private User buildUser(String userId, String username, String nickname) {
        return User.builder()
                .userId(userId)
                .username(username)
                .nickname(nickname)
                .phone("13800000000")
                .email(username + "@demo.com")
                .gender("M")
                .birthday(LocalDate.of(2000, 1, 1))
                .registerTime(LocalDateTime.now().minusDays(30))
                .status(User.Status.NORMAL.getCode())
                .level(User.Level.BRONZE.getCode())
                .points(0)
                .balance(new BigDecimal("1000.00"))
                .growthValue(0)
                .loginCount(0)
                .totalOrderAmount(BigDecimal.ZERO)
                .addresses(List.of(User.UserAddress.builder()
                        .addressId("ADDR1")
                        .receiver(nickname)
                        .phone("13800000000")
                        .province("广西")
                        .city("桂林")
                        .district("七星区")
                        .detail("GUET")
                        .postcode("541004")
                        .isDefault(true)
                        .createTime(LocalDateTime.now().minusDays(30))
                        .build()))
                .build();
    }

    private int initSalesData() throws IOException {
        int inserted = 0;

        LocalDate today = LocalDate.now();
        for (int i = 0; i < 7; i++) {
            LocalDate date = today.minusDays(i);
            inserted += initDailyTotalIfMissing(date);
        }

        return inserted;
    }

    private int initDailyTotalIfMissing(LocalDate date) throws IOException {
        String rowKey = date.format(DateTimeFormatter.ISO_LOCAL_DATE) + "_TOTAL";
        if (salesDataRepository.findById(rowKey) != null) {
            return 0;
        }

        long saleCount = 50L + (long) (Math.random() * 50);
        BigDecimal saleAmount = new BigDecimal("10000.00").add(new BigDecimal(String.valueOf((int) (Math.random() * 20000))));
        long refundCount = (long) (Math.random() * 5);
        BigDecimal refundAmount = new BigDecimal("0.00");

        SalesData salesData = SalesData.builder()
                .date(date)
                .saleCount(saleCount)
                .saleAmount(saleAmount)
                .refundCount(refundCount)
                .refundAmount(refundAmount)
                .build();

        salesDataRepository.save(salesData);
        return 1;
    }

    private int initOrders() throws IOException {
        List<Order> orders = new ArrayList<>();

        orders.add(buildOrder("ORD_DEMO_001", "U001", "张三", "13800000000", "广西桂林市七星区", "541004",
                List.of(buildOrderItem("P1001", "iPhone 15", new BigDecimal("6999.00"), 1),
                        buildOrderItem("P3001", "AirPods Pro", new BigDecimal("1899.00"), 1))));

        orders.add(buildOrder("ORD_DEMO_002", "U002", "李四", "13800000000", "广西桂林市七星区", "541004",
                List.of(buildOrderItem("P2001", "联想小新 Pro", new BigDecimal("5299.00"), 1))));

        orders.add(buildOrder("ORD_DEMO_003", "U003", "王五", "13800000000", "广西桂林市七星区", "541004",
                List.of(buildOrderItem("P5002", "无线鼠标", new BigDecimal("399.00"), 2),
                        buildOrderItem("P5001", "机械键盘", new BigDecimal("699.00"), 1))));

        int inserted = 0;
        for (Order o : orders) {
            if (!orderRepository.existsById(o.getOrderId())) {
                orderRepository.save(o);
                inserted++;
            }
        }

        return inserted;
    }

    private Order buildOrder(String orderId, String userId, String receiver, String phone, String address, String postcode, List<Order.OrderItem> items) {
        BigDecimal total = BigDecimal.ZERO;
        for (Order.OrderItem item : items) {
            total = total.add(item.getAmount() != null ? item.getAmount() : BigDecimal.ZERO);
        }

        BigDecimal discount = BigDecimal.ZERO;
        BigDecimal actual = total.subtract(discount);

        return Order.builder()
                .orderId(orderId)
                .userId(userId)
                .receiver(receiver)
                .phone(phone)
                .address(address)
                .postcode(postcode)
                .items(items)
                .discountAmount(discount)
                .totalAmount(total)
                .actualAmount(actual)
                .status(Order.Status.PENDING_PAYMENT.getCode())
                .payMethod(Order.PayMethod.ALIPAY.getCode())
                .createTime(LocalDateTime.now().minusHours(2))
                .build();
    }

    private Order.OrderItem buildOrderItem(String productId, String productName, BigDecimal price, int quantity) {
        BigDecimal amount = price.multiply(new BigDecimal(quantity));
        return Order.OrderItem.builder()
                .productId(productId)
                .productName(productName)
                .price(price)
                .quantity(quantity)
                .amount(amount)
                .build();
    }

    private void initRedisAuxData() {
        try {
            String userId = "U001";
            cartService.addToCart(userId, CartItem.builder()
                    .userId(userId)
                    .productId("P1002")
                    .productName("小米 14")
                    .price(new BigDecimal("3999.00"))
                    .quantity(1)
                    .selected(true)
                    .build());

            rankingService.addHotProductScore("P1001", 10.0);
            rankingService.addHotProductScore("P3001", 7.0);
            rankingService.addHotProductScore("P5002", 4.0);

            LocalDate today = LocalDate.now();
            String dateKey = today.format(DateTimeFormatter.BASIC_ISO_DATE);
            String dashboardKey = RedisConfig.RedisKeys.DASHBOARD_PREFIX + dateKey;
            if (!redisService.exists(dashboardKey)) {
                Map<String, Object> map = new HashMap<>();
                map.put("total_amount", "15888.50");
                map.put("order_count", "12");
                map.put("user_count", "8");
                map.put("avg_price", "1324.04");
                redisService.hset(dashboardKey, map);
                redisService.expire(dashboardKey, 3600, java.util.concurrent.TimeUnit.SECONDS);
            }

            if (!redisService.exists(RedisConfig.RedisKeys.STAT_ORDERS_TODAY)) {
                redisService.set(RedisConfig.RedisKeys.STAT_ORDERS_TODAY, 12);
                redisService.expire(RedisConfig.RedisKeys.STAT_ORDERS_TODAY, 3600, java.util.concurrent.TimeUnit.SECONDS);
            }
            if (!redisService.exists(RedisConfig.RedisKeys.STAT_SALES_TODAY)) {
                redisService.set(RedisConfig.RedisKeys.STAT_SALES_TODAY, "15888.50");
                redisService.expire(RedisConfig.RedisKeys.STAT_SALES_TODAY, 3600, java.util.concurrent.TimeUnit.SECONDS);
            }

            stockService.setSeckillStock("S20260105", "P1001", 20);
        } catch (Exception e) {
            log.error("Failed to init Redis demo data", e);
        }
    }

    @Data
    public static class InitResult {
        private int productsInserted;
        private int ordersInserted;
        private int usersInserted;
        private int salesDataInserted;
    }
}
