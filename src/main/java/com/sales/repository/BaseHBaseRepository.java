package com.sales.repository;

import com.sales.utils.JsonUtils;
import com.sales.config.HBaseConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.*;
import org.apache.hadoop.hbase.util.Bytes;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Repository
public abstract class BaseHBaseRepository {

    @Autowired
    protected Connection connection;

    protected Table getTable(TableName tableName) throws IOException {
        if (connection == null) {
            throw new IOException("HBase connection is not available (Connection bean is null). Please check HBase configuration and connectivity.");
        }
        return connection.getTable(tableName);
    }

    protected void closeTable(Table table) {
        if (table != null) {
            try {
                table.close();
            } catch (IOException e) {
                log.error("Failed to close HBase table", e);
            }
        }
    }

    protected Put createPut(String rowKey) {
        return new Put(Bytes.toBytes(rowKey));
    }

    protected Get createGet(String rowKey) {
        return new Get(Bytes.toBytes(rowKey));
    }

    protected Delete createDelete(String rowKey) {
        return new Delete(Bytes.toBytes(rowKey));
    }

    protected Scan createScan() {
        return new Scan();
    }

    protected void addColumn(Put put, String family, String qualifier, String value) {
        if (value != null) {
            put.addColumn(Bytes.toBytes(family), Bytes.toBytes(qualifier), Bytes.toBytes(value));
        }
    }

    protected void addColumn(Put put, String family, String qualifier, Long value) {
        if (value != null) {
            put.addColumn(Bytes.toBytes(family), Bytes.toBytes(qualifier), Bytes.toBytes(value));
        }
    }

    protected void addColumn(Put put, String family, String qualifier, Integer value) {
        if (value != null) {
            put.addColumn(Bytes.toBytes(family), Bytes.toBytes(qualifier), Bytes.toBytes(value));
        }
    }

    protected void addColumn(Put put, String family, String qualifier, Double value) {
        if (value != null) {
            put.addColumn(Bytes.toBytes(family), Bytes.toBytes(qualifier), Bytes.toBytes(value));
        }
    }

    protected void addJsonColumn(Put put, String family, String qualifier, Object value) {
        if (value != null) {
            String jsonValue = JsonUtils.toJson(value);
            put.addColumn(Bytes.toBytes(family), Bytes.toBytes(qualifier), Bytes.toBytes(jsonValue));
        }
    }

    protected String getString(Result result, String family, String qualifier) {
        byte[] bytes = result.getValue(Bytes.toBytes(family), Bytes.toBytes(qualifier));
        return bytes != null ? Bytes.toString(bytes) : null;
    }

    protected Long getLong(Result result, String family, String qualifier) {
        byte[] bytes = result.getValue(Bytes.toBytes(family), Bytes.toBytes(qualifier));
        return bytes != null ? Bytes.toLong(bytes) : null;
    }

    protected Integer getInteger(Result result, String family, String qualifier) {
        byte[] bytes = result.getValue(Bytes.toBytes(family), Bytes.toBytes(qualifier));
        return bytes != null ? Bytes.toInt(bytes) : null;
    }

    protected Double getDouble(Result result, String family, String qualifier) {
        byte[] bytes = result.getValue(Bytes.toBytes(family), Bytes.toBytes(qualifier));
        return bytes != null ? Bytes.toDouble(bytes) : null;
    }

    protected <T> T getJson(Result result, String family, String qualifier, Class<T> clazz) {
        String jsonValue = getString(result, family, qualifier);
        if (jsonValue != null && !jsonValue.isEmpty()) {
            try {
                return JsonUtils.fromJson(jsonValue, clazz);
            } catch (Exception e) {
                log.error("Failed to parse JSON for column {}:{}", family, qualifier, e);
            }
        }
        return null;
    }

    protected void putData(TableName tableName, Put put) throws IOException {
        try (Table table = getTable(tableName)) {
            table.put(put);
        }
    }

    protected Result getData(TableName tableName, Get get) throws IOException {
        try (Table table = getTable(tableName)) {
            return table.get(get);
        }
    }

    protected List<Result> scanData(TableName tableName, Scan scan) throws IOException {
        try (Table table = getTable(tableName);
             ResultScanner scanner = table.getScanner(scan)) {
            List<Result> results = new ArrayList<>();
            for (Result result : scanner) {
                results.add(result);
            }
            return results;
        }
    }

    protected void deleteData(TableName tableName, Delete delete) throws IOException {
        try (Table table = getTable(tableName)) {
            table.delete(delete);
        }
    }

    protected boolean exists(TableName tableName, Get get) throws IOException {
        try (Table table = getTable(tableName)) {
            return table.exists(get);
        }
    }

    protected void batchPut(TableName tableName, List<Put> puts) throws IOException {
        if (puts == null || puts.isEmpty()) {
            return;
        }
        
        try (Table table = getTable(tableName)) {
            Object[] results = new Object[puts.size()];
            table.batch(puts, results);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Batch put interrupted", e);
        }
    }

    protected void incrementColumnValue(TableName tableName, String rowKey, 
                                      String family, String qualifier, long amount) throws IOException {
        try (Table table = getTable(tableName)) {
            Increment increment = new Increment(Bytes.toBytes(rowKey));
            increment.addColumn(Bytes.toBytes(family), Bytes.toBytes(qualifier), amount);
            table.increment(increment);
        }
    }
}
