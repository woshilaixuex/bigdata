package com.sales.repository;

import com.sales.config.HBaseConfig;
import com.sales.entity.Order;
import lombok.extern.slf4j.Slf4j;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.*;
import org.apache.hadoop.hbase.filter.CompareFilter;
import org.apache.hadoop.hbase.filter.PageFilter;
import org.apache.hadoop.hbase.filter.SingleColumnValueFilter;
import org.apache.hadoop.hbase.util.Bytes;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Repository
public class OrderRepository extends BaseHBaseRepository {

    private static final TableName TABLE_NAME = HBaseConfig.TableNames.ORDER_HISTORY;

    public void save(Order order) throws IOException {
        Put put = createPut(order.getOrderId());
        
        // 基本信息
        addColumn(put, HBaseConfig.ColumnFamilies.CF_BASE, HBaseConfig.Columns.ORDER_USER_ID, order.getUserId());
        addColumn(put, HBaseConfig.ColumnFamilies.CF_BASE, HBaseConfig.Columns.ORDER_TOTAL_AMOUNT, 
                 order.getTotalAmount() != null ? order.getTotalAmount().doubleValue() : null);
        addColumn(put, HBaseConfig.ColumnFamilies.CF_BASE, HBaseConfig.Columns.ORDER_DISCOUNT_AMOUNT, 
                 order.getDiscountAmount() != null ? order.getDiscountAmount().doubleValue() : null);
        addColumn(put, HBaseConfig.ColumnFamilies.CF_BASE, HBaseConfig.Columns.ORDER_ACTUAL_AMOUNT, 
                 order.getActualAmount() != null ? order.getActualAmount().doubleValue() : null);
        addColumn(put, HBaseConfig.ColumnFamilies.CF_BASE, HBaseConfig.Columns.ORDER_STATUS, order.getStatus());
        addColumn(put, HBaseConfig.ColumnFamilies.CF_BASE, HBaseConfig.Columns.ORDER_PAY_METHOD, order.getPayMethod());
        addColumn(put, HBaseConfig.ColumnFamilies.CF_BASE, HBaseConfig.Columns.ORDER_CREATE_TIME, 
                 formatDateTime(order.getCreateTime()));
        addColumn(put, HBaseConfig.ColumnFamilies.CF_BASE, HBaseConfig.Columns.ORDER_PAY_TIME, 
                 formatDateTime(order.getPayTime()));
        addColumn(put, HBaseConfig.ColumnFamilies.CF_BASE, HBaseConfig.Columns.ORDER_DELIVER_TIME, 
                 formatDateTime(order.getDeliverTime()));
        addColumn(put, HBaseConfig.ColumnFamilies.CF_BASE, HBaseConfig.Columns.ORDER_COMPLETE_TIME, 
                 formatDateTime(order.getCompleteTime()));
        
        // 收货信息
        addColumn(put, HBaseConfig.ColumnFamilies.CF_ADDRESS, HBaseConfig.Columns.ORDER_RECEIVER, order.getReceiver());
        addColumn(put, HBaseConfig.ColumnFamilies.CF_ADDRESS, HBaseConfig.Columns.ORDER_PHONE, order.getPhone());
        addColumn(put, HBaseConfig.ColumnFamilies.CF_ADDRESS, HBaseConfig.Columns.ORDER_ADDRESS, order.getAddress());
        addColumn(put, HBaseConfig.ColumnFamilies.CF_ADDRESS, HBaseConfig.Columns.ORDER_POSTCODE, order.getPostcode());
        
        // 商品明细（动态列存储）
        if (order.getItems() != null) {
            for (int i = 0; i < order.getItems().size(); i++) {
                Order.OrderItem item = order.getItems().get(i);
                String qualifier = "item_" + (i + 1);
                addJsonColumn(put, HBaseConfig.ColumnFamilies.CF_ITEMS, qualifier, item);
            }
        }
        
        // 物流信息
        addColumn(put, HBaseConfig.ColumnFamilies.CF_LOGISTICS, HBaseConfig.Columns.ORDER_EXPRESS_COMPANY, order.getExpressCompany());
        addColumn(put, HBaseConfig.ColumnFamilies.CF_LOGISTICS, HBaseConfig.Columns.ORDER_EXPRESS_NO, order.getExpressNo());
        addJsonColumn(put, HBaseConfig.ColumnFamilies.CF_LOGISTICS, HBaseConfig.Columns.ORDER_LOGISTICS_INFO, order.getLogisticsInfo());
        
        putData(TABLE_NAME, put);
        log.info("Order saved: {}", order.getOrderId());
    }

    public Order findById(String orderId) throws IOException {
        Get get = createGet(orderId);
        Result result = getData(TABLE_NAME, get);
        
        if (result.isEmpty()) {
            return null;
        }
        
        return mapToOrder(result);
    }

    public List<Order> findByUserId(String userId, int limit) throws IOException {
        Scan scan = createScan();
        
        // 添加用户ID过滤器
        SingleColumnValueFilter userFilter = new SingleColumnValueFilter(
                Bytes.toBytes(HBaseConfig.ColumnFamilies.CF_BASE),
                Bytes.toBytes(HBaseConfig.Columns.ORDER_USER_ID),
                CompareFilter.CompareOp.EQUAL,
                Bytes.toBytes(userId)
        );
        
        scan.setFilter(userFilter);
        scan.setReversed(true); // 按时间倒序
        scan.setLimit(limit);
        
        List<Result> results = scanData(TABLE_NAME, scan);
        List<Order> orders = new ArrayList<>();
        
        for (Result result : results) {
            orders.add(mapToOrder(result));
        }
        
        return orders;
    }

    public List<Order> findByStatus(Integer status, int limit) throws IOException {
        Scan scan = createScan();
        
        // 添加状态过滤器
        SingleColumnValueFilter statusFilter = new SingleColumnValueFilter(
                Bytes.toBytes(HBaseConfig.ColumnFamilies.CF_BASE),
                Bytes.toBytes(HBaseConfig.Columns.ORDER_STATUS),
                CompareFilter.CompareOp.EQUAL,
                Bytes.toBytes(status)
        );
        
        scan.setFilter(statusFilter);
        scan.setReversed(true);
        scan.setLimit(limit);
        
        List<Result> results = scanData(TABLE_NAME, scan);
        List<Order> orders = new ArrayList<>();
        
        for (Result result : results) {
            orders.add(mapToOrder(result));
        }
        
        return orders;
    }

    public List<Order> findRecentOrders(int limit) throws IOException {
        Scan scan = createScan();
        scan.setReversed(true); // 按时间倒序
        scan.setLimit(limit);
        
        List<Result> results = scanData(TABLE_NAME, scan);
        List<Order> orders = new ArrayList<>();
        
        for (Result result : results) {
            orders.add(mapToOrder(result));
        }
        
        return orders;
    }

    public void updateStatus(String orderId, Integer status) throws IOException {
        Put put = createPut(orderId);
        addColumn(put, HBaseConfig.ColumnFamilies.CF_BASE, HBaseConfig.Columns.ORDER_STATUS, status);
        
        // 根据状态更新相应的时间字段
        LocalDateTime now = LocalDateTime.now();
        if (Order.Status.PENDING_DELIVERY.getCode().equals(status)) {
            addColumn(put, HBaseConfig.ColumnFamilies.CF_BASE, HBaseConfig.Columns.ORDER_PAY_TIME, formatDateTime(now));
        } else if (Order.Status.SHIPPED.getCode().equals(status)) {
            addColumn(put, HBaseConfig.ColumnFamilies.CF_BASE, HBaseConfig.Columns.ORDER_DELIVER_TIME, formatDateTime(now));
        } else if (Order.Status.COMPLETED.getCode().equals(status)) {
            addColumn(put, HBaseConfig.ColumnFamilies.CF_BASE, HBaseConfig.Columns.ORDER_COMPLETE_TIME, formatDateTime(now));
        }
        
        putData(TABLE_NAME, put);
        log.info("Order status updated: {} -> {}", orderId, status);
    }

    public void updateLogistics(String orderId, String expressCompany, String expressNo) throws IOException {
        Put put = createPut(orderId);
        addColumn(put, HBaseConfig.ColumnFamilies.CF_LOGISTICS, HBaseConfig.Columns.ORDER_EXPRESS_COMPANY, expressCompany);
        addColumn(put, HBaseConfig.ColumnFamilies.CF_LOGISTICS, HBaseConfig.Columns.ORDER_EXPRESS_NO, expressNo);
        
        putData(TABLE_NAME, put);
        log.info("Order logistics updated: {}", orderId);
    }

    public boolean existsById(String orderId) throws IOException {
        Get get = createGet(orderId);
        return exists(TABLE_NAME, get);
    }

    public long countByStatus(Integer status) throws IOException {
        Scan scan = createScan();
        
        // 添加状态过滤器
        SingleColumnValueFilter statusFilter = new SingleColumnValueFilter(
                Bytes.toBytes(HBaseConfig.ColumnFamilies.CF_BASE),
                Bytes.toBytes(HBaseConfig.Columns.ORDER_STATUS),
                CompareFilter.CompareOp.EQUAL,
                Bytes.toBytes(status)
        );
        
        scan.setFilter(statusFilter);
        
        List<Result> results = scanData(TABLE_NAME, scan);
        return results.size();
    }
    
    /**
     * 删除订单
     */
    public void delete(String orderId) throws IOException {
        Delete delete = createDelete(orderId);
        deleteData(TABLE_NAME, delete);
        log.info("Order deleted: {}", orderId);
    }

    private Order mapToOrder(Result result) {
        Order.OrderBuilder builder = Order.builder();
        
        String orderId = Bytes.toString(result.getRow());
        builder.orderId(orderId);
        
        // 基本信息
        builder.userId(getString(result, HBaseConfig.ColumnFamilies.CF_BASE, HBaseConfig.Columns.ORDER_USER_ID));
        
        Double totalAmount = getDouble(result, HBaseConfig.ColumnFamilies.CF_BASE, HBaseConfig.Columns.ORDER_TOTAL_AMOUNT);
        if (totalAmount != null) {
            builder.totalAmount(BigDecimal.valueOf(totalAmount));
        }
        
        Double discountAmount = getDouble(result, HBaseConfig.ColumnFamilies.CF_BASE, HBaseConfig.Columns.ORDER_DISCOUNT_AMOUNT);
        if (discountAmount != null) {
            builder.discountAmount(BigDecimal.valueOf(discountAmount));
        }
        
        Double actualAmount = getDouble(result, HBaseConfig.ColumnFamilies.CF_BASE, HBaseConfig.Columns.ORDER_ACTUAL_AMOUNT);
        if (actualAmount != null) {
            builder.actualAmount(BigDecimal.valueOf(actualAmount));
        }
        
        builder.status(getInteger(result, HBaseConfig.ColumnFamilies.CF_BASE, HBaseConfig.Columns.ORDER_STATUS));
        builder.payMethod(getString(result, HBaseConfig.ColumnFamilies.CF_BASE, HBaseConfig.Columns.ORDER_PAY_METHOD));
        builder.createTime(parseDateTime(getString(result, HBaseConfig.ColumnFamilies.CF_BASE, HBaseConfig.Columns.ORDER_CREATE_TIME)));
        builder.payTime(parseDateTime(getString(result, HBaseConfig.ColumnFamilies.CF_BASE, HBaseConfig.Columns.ORDER_PAY_TIME)));
        builder.deliverTime(parseDateTime(getString(result, HBaseConfig.ColumnFamilies.CF_BASE, HBaseConfig.Columns.ORDER_DELIVER_TIME)));
        builder.completeTime(parseDateTime(getString(result, HBaseConfig.ColumnFamilies.CF_BASE, HBaseConfig.Columns.ORDER_COMPLETE_TIME)));
        
        // 收货信息
        builder.receiver(getString(result, HBaseConfig.ColumnFamilies.CF_ADDRESS, HBaseConfig.Columns.ORDER_RECEIVER));
        builder.phone(getString(result, HBaseConfig.ColumnFamilies.CF_ADDRESS, HBaseConfig.Columns.ORDER_PHONE));
        builder.address(getString(result, HBaseConfig.ColumnFamilies.CF_ADDRESS, HBaseConfig.Columns.ORDER_ADDRESS));
        builder.postcode(getString(result, HBaseConfig.ColumnFamilies.CF_ADDRESS, HBaseConfig.Columns.ORDER_POSTCODE));
        
        // 商品明细
        List<Order.OrderItem> items = new ArrayList<>();
        // 动态读取item列
        for (int i = 1; i <= 50; i++) { // 假设最多50个商品
            String qualifier = "item_" + i;
            Order.OrderItem item = getJson(result, HBaseConfig.ColumnFamilies.CF_ITEMS, qualifier, Order.OrderItem.class);
            if (item != null) {
                items.add(item);
            }
        }
        builder.items(items);
        
        // 物流信息
        builder.expressCompany(getString(result, HBaseConfig.ColumnFamilies.CF_LOGISTICS, HBaseConfig.Columns.ORDER_EXPRESS_COMPANY));
        builder.expressNo(getString(result, HBaseConfig.ColumnFamilies.CF_LOGISTICS, HBaseConfig.Columns.ORDER_EXPRESS_NO));
        builder.logisticsInfo(getJson(result, HBaseConfig.ColumnFamilies.CF_LOGISTICS, HBaseConfig.Columns.ORDER_LOGISTICS_INFO, List.class));
        
        return builder.build();
    }

    private String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    private LocalDateTime parseDateTime(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.isEmpty()) {
            return null;
        }
        try {
            return LocalDateTime.parse(dateTimeStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (DateTimeParseException ignored) {
            try {
                DateTimeFormatter legacyFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                return LocalDateTime.parse(dateTimeStr, legacyFormatter);
            } catch (Exception e) {
                log.error("Failed to parse date time: {}", dateTimeStr, e);
                return null;
            }
        } catch (Exception e) {
            log.error("Failed to parse date time: {}", dateTimeStr, e);
            return null;
        }
    }
}
