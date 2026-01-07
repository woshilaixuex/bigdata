package com.sales.repository;

import com.sales.config.HBaseConfig;
import com.sales.entity.Product;
import lombok.extern.slf4j.Slf4j;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.*;
import org.apache.hadoop.hbase.filter.CompareFilter;
import org.apache.hadoop.hbase.filter.FilterList;
import org.apache.hadoop.hbase.filter.PageFilter;
import org.apache.hadoop.hbase.filter.SingleColumnValueFilter;
import org.apache.hadoop.hbase.util.Bytes;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Repository
public class ProductRepository extends BaseHBaseRepository {

    private static final TableName TABLE_NAME = HBaseConfig.TableNames.PRODUCT_INFO;

    public void save(Product product) throws IOException {
        Put put = createPut(product.getProductId());
        
        // 基本信息
        addColumn(put, HBaseConfig.ColumnFamilies.CF_BASE, HBaseConfig.Columns.PRODUCT_NAME, product.getName());
        addColumn(put, HBaseConfig.ColumnFamilies.CF_BASE, HBaseConfig.Columns.PRODUCT_CATEGORY, product.getCategory());
        addColumn(put, HBaseConfig.ColumnFamilies.CF_BASE, HBaseConfig.Columns.PRODUCT_BRAND, product.getBrand());
        addColumn(put, HBaseConfig.ColumnFamilies.CF_BASE, HBaseConfig.Columns.PRODUCT_PRICE, 
                 product.getPrice() != null ? product.getPrice().doubleValue() : null);
        addColumn(put, HBaseConfig.ColumnFamilies.CF_BASE, HBaseConfig.Columns.PRODUCT_COST, 
                 product.getCost() != null ? product.getCost().doubleValue() : null);
        addColumn(put, HBaseConfig.ColumnFamilies.CF_BASE, HBaseConfig.Columns.PRODUCT_STATUS, product.getStatus());
        addColumn(put, HBaseConfig.ColumnFamilies.CF_BASE, HBaseConfig.Columns.PRODUCT_CREATE_TIME, 
                 formatDateTime(product.getCreateTime()));
        
        // 详细信息
        addColumn(put, HBaseConfig.ColumnFamilies.CF_DETAIL, HBaseConfig.Columns.PRODUCT_DESCRIPTION, product.getDescription());
        addColumn(put, HBaseConfig.ColumnFamilies.CF_DETAIL, HBaseConfig.Columns.PRODUCT_SPEC, product.getSpec());
        addJsonColumn(put, HBaseConfig.ColumnFamilies.CF_DETAIL, HBaseConfig.Columns.PRODUCT_IMAGES, product.getImages());
        addColumn(put, HBaseConfig.ColumnFamilies.CF_DETAIL, HBaseConfig.Columns.PRODUCT_TAGS, product.getTags());
        
        // 库存信息
        addColumn(put, HBaseConfig.ColumnFamilies.CF_STOCK, HBaseConfig.Columns.PRODUCT_TOTAL_STOCK, product.getTotalStock());
        addJsonColumn(put, HBaseConfig.ColumnFamilies.CF_STOCK, HBaseConfig.Columns.PRODUCT_WAREHOUSE_STOCK, product.getWarehouseStock());
        addColumn(put, HBaseConfig.ColumnFamilies.CF_STOCK, HBaseConfig.Columns.PRODUCT_SAFE_STOCK, product.getSafeStock());
        addColumn(put, HBaseConfig.ColumnFamilies.CF_STOCK, HBaseConfig.Columns.PRODUCT_LOCK_STOCK, product.getLockStock());
        
        // 统计信息
        addColumn(put, HBaseConfig.ColumnFamilies.CF_STAT, HBaseConfig.Columns.PRODUCT_VIEW_COUNT, product.getViewCount());
        addColumn(put, HBaseConfig.ColumnFamilies.CF_STAT, HBaseConfig.Columns.PRODUCT_SALE_COUNT, product.getSaleCount());
        addColumn(put, HBaseConfig.ColumnFamilies.CF_STAT, HBaseConfig.Columns.PRODUCT_COLLECT_COUNT, product.getCollectCount());
        addColumn(put, HBaseConfig.ColumnFamilies.CF_STAT, HBaseConfig.Columns.PRODUCT_UPDATE_TIME, 
                 formatDateTime(product.getUpdateTime()));
        
        putData(TABLE_NAME, put);
        log.info("Product saved: {}", product.getProductId());
    }

    public Product findById(String productId) throws IOException {
        Get get = createGet(productId);
        Result result = getData(TABLE_NAME, get);
        
        if (result.isEmpty()) {
            return null;
        }
        
        return mapToProduct(result);
    }

    public List<Product> findAll(int limit) throws IOException {
        Scan scan = createScan();
        scan.setLimit(limit);
        
        List<Result> results = scanData(TABLE_NAME, scan);
        List<Product> products = new ArrayList<>();
        
        for (Result result : results) {
            products.add(mapToProduct(result));
        }
        
        return products;
    }

    public List<Product> findByCategory(String category, int limit) throws IOException {
        Scan scan = createScan();
        
        // 添加分类过滤器
        SingleColumnValueFilter categoryFilter = new SingleColumnValueFilter(
                Bytes.toBytes(HBaseConfig.ColumnFamilies.CF_BASE),
                Bytes.toBytes(HBaseConfig.Columns.PRODUCT_CATEGORY),
                CompareFilter.CompareOp.EQUAL,
                Bytes.toBytes(category)
        );
        
        FilterList filterList = new FilterList(FilterList.Operator.MUST_PASS_ONE);
        filterList.addFilter(categoryFilter);
        scan.setFilter(filterList);
        scan.setLimit(limit);
        
        List<Result> results = scanData(TABLE_NAME, scan);
        List<Product> products = new ArrayList<>();
        
        for (Result result : results) {
            products.add(mapToProduct(result));
        }
        
        return products;
    }

    public List<Product> findByStatus(Integer status, int limit) throws IOException {
        Scan scan = createScan();
        
        // 添加状态过滤器
        SingleColumnValueFilter statusFilter = new SingleColumnValueFilter(
                Bytes.toBytes(HBaseConfig.ColumnFamilies.CF_BASE),
                Bytes.toBytes(HBaseConfig.Columns.PRODUCT_STATUS),
                CompareFilter.CompareOp.EQUAL,
                Bytes.toBytes(status)
        );
        
        scan.setFilter(statusFilter);
        scan.setLimit(limit);
        
        List<Result> results = scanData(TABLE_NAME, scan);
        List<Product> products = new ArrayList<>();
        
        for (Result result : results) {
            products.add(mapToProduct(result));
        }
        
        return products;
    }

    public List<Product> findByNameContaining(String name, int limit) throws IOException {
        Scan scan = createScan();
        
        // 注意：HBase不支持模糊查询，这里需要使用其他策略
        // 实际项目中可以考虑使用Solr或ElasticSearch进行全文搜索
        scan.setLimit(limit);
        
        List<Result> results = scanData(TABLE_NAME, scan);
        List<Product> products = new ArrayList<>();
        
        for (Result result : results) {
            Product product = mapToProduct(result);
            if (product.getName() != null && product.getName().contains(name)) {
                products.add(product);
            }
        }
        
        return products;
    }

    public void updateStock(String productId, Integer stock) throws IOException {
        Put put = createPut(productId);
        addColumn(put, HBaseConfig.ColumnFamilies.CF_STOCK, HBaseConfig.Columns.PRODUCT_TOTAL_STOCK, stock);
        addColumn(put, HBaseConfig.ColumnFamilies.CF_STAT, HBaseConfig.Columns.PRODUCT_UPDATE_TIME, 
                 formatDateTime(LocalDateTime.now()));
        
        putData(TABLE_NAME, put);
    }

    public void incrementViewCount(String productId) throws IOException {
        incrementColumnValue(TABLE_NAME, productId, 
                            HBaseConfig.ColumnFamilies.CF_STAT, 
                            HBaseConfig.Columns.PRODUCT_VIEW_COUNT, 1L);
    }

    public void incrementSaleCount(String productId, Long quantity) throws IOException {
        incrementColumnValue(TABLE_NAME, productId, 
                            HBaseConfig.ColumnFamilies.CF_STAT, 
                            HBaseConfig.Columns.PRODUCT_SALE_COUNT, quantity);
    }

    public void deleteById(String productId) throws IOException {
        Delete delete = createDelete(productId);
        deleteData(TABLE_NAME, delete);
        log.info("Product deleted: {}", productId);
    }

    public boolean existsById(String productId) throws IOException {
        Get get = createGet(productId);
        return exists(TABLE_NAME, get);
    }

    private Product mapToProduct(Result result) {
        Product.ProductBuilder builder = Product.builder();
        
        String productId = Bytes.toString(result.getRow());
        builder.productId(productId);
        
        // 基本信息
        builder.name(getString(result, HBaseConfig.ColumnFamilies.CF_BASE, HBaseConfig.Columns.PRODUCT_NAME));
        builder.category(getString(result, HBaseConfig.ColumnFamilies.CF_BASE, HBaseConfig.Columns.PRODUCT_CATEGORY));
        builder.brand(getString(result, HBaseConfig.ColumnFamilies.CF_BASE, HBaseConfig.Columns.PRODUCT_BRAND));
        
        Double price = getDouble(result, HBaseConfig.ColumnFamilies.CF_BASE, HBaseConfig.Columns.PRODUCT_PRICE);
        if (price != null) {
            builder.price(BigDecimal.valueOf(price));
        }
        
        Double cost = getDouble(result, HBaseConfig.ColumnFamilies.CF_BASE, HBaseConfig.Columns.PRODUCT_COST);
        if (cost != null) {
            builder.cost(BigDecimal.valueOf(cost));
        }
        
        builder.status(getInteger(result, HBaseConfig.ColumnFamilies.CF_BASE, HBaseConfig.Columns.PRODUCT_STATUS));
        builder.createTime(parseDateTime(getString(result, HBaseConfig.ColumnFamilies.CF_BASE, HBaseConfig.Columns.PRODUCT_CREATE_TIME)));
        
        // 详细信息
        builder.description(getString(result, HBaseConfig.ColumnFamilies.CF_DETAIL, HBaseConfig.Columns.PRODUCT_DESCRIPTION));
        builder.spec(getString(result, HBaseConfig.ColumnFamilies.CF_DETAIL, HBaseConfig.Columns.PRODUCT_SPEC));
        builder.images(getJson(result, HBaseConfig.ColumnFamilies.CF_DETAIL, HBaseConfig.Columns.PRODUCT_IMAGES, List.class));
        builder.tags(getString(result, HBaseConfig.ColumnFamilies.CF_DETAIL, HBaseConfig.Columns.PRODUCT_TAGS));
        
        // 库存信息
        builder.totalStock(getInteger(result, HBaseConfig.ColumnFamilies.CF_STOCK, HBaseConfig.Columns.PRODUCT_TOTAL_STOCK));
        builder.warehouseStock(getJson(result, HBaseConfig.ColumnFamilies.CF_STOCK, HBaseConfig.Columns.PRODUCT_WAREHOUSE_STOCK, Map.class));
        builder.safeStock(getInteger(result, HBaseConfig.ColumnFamilies.CF_STOCK, HBaseConfig.Columns.PRODUCT_SAFE_STOCK));
        builder.lockStock(getInteger(result, HBaseConfig.ColumnFamilies.CF_STOCK, HBaseConfig.Columns.PRODUCT_LOCK_STOCK));
        
        // 统计信息
        builder.viewCount(getLong(result, HBaseConfig.ColumnFamilies.CF_STAT, HBaseConfig.Columns.PRODUCT_VIEW_COUNT));
        builder.saleCount(getLong(result, HBaseConfig.ColumnFamilies.CF_STAT, HBaseConfig.Columns.PRODUCT_SALE_COUNT));
        builder.collectCount(getLong(result, HBaseConfig.ColumnFamilies.CF_STAT, HBaseConfig.Columns.PRODUCT_COLLECT_COUNT));
        builder.updateTime(parseDateTime(getString(result, HBaseConfig.ColumnFamilies.CF_STAT, HBaseConfig.Columns.PRODUCT_UPDATE_TIME)));
        
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
        // 兼容多种常见格式：ISO 与 'yyyy-MM-dd HH:mm:ss'
        List<DateTimeFormatter> formatters = List.of(
                DateTimeFormatter.ISO_LOCAL_DATE_TIME,
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        );
        for (DateTimeFormatter formatter : formatters) {
            try {
                return LocalDateTime.parse(dateTimeStr, formatter);
            } catch (Exception ignored) {
            }
        }
        // 尝试去掉毫秒/尾部Z等简单清洗后再试
        try {
            String cleaned = dateTimeStr.replace('T', ' ');
            if (cleaned.length() > 19) {
                cleaned = cleaned.substring(0, 19);
            }
            return LocalDateTime.parse(cleaned, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (Exception e) {
            log.warn("Unparsable dateTime string: '{}'", dateTimeStr);
            return null;
        }
    }
}
