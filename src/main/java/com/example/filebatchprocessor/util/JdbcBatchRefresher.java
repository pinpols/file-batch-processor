package com.example.filebatchprocessor.util;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ReflectUtil;
import cn.hutool.poi.excel.ExcelReader;
import cn.hutool.poi.excel.ExcelUtil;
import jakarta.persistence.Column;
import jakarta.persistence.Table;
import lombok.Data;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.InputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import jakarta.persistence.*;


@Component
public class JdbcBatchRefresher {


    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    public JdbcBatchRefresher(JdbcTemplate jdbcTemplate,
                              PlatformTransactionManager txManager) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(txManager);
    }

    private static final Map<Class<?>, EntityMeta> CACHE = new ConcurrentHashMap<>();

    /**
     * Excel -> List<T>
     */
    public <T> List<T> readExcel(InputStream inputStream, Class<T> clazz) {

        ExcelReader reader = ExcelUtil.getReader(inputStream);
        List<Map<String, Object>> rows = reader.readAll();

        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }

        return rows.stream()
                .map(row -> {
                    T entity = ReflectUtil.newInstance(clazz);
                    BeanUtil.fillBeanWithMap(row, entity, true);
                    return entity;
                })
                .collect(Collectors.toList());
    }

    /**
     * 全量覆盖（支持大数据分批提交）
     */
    public <T> void refresh(List<T> list) {

        if (list == null || list.isEmpty()) {
            return;
        }

        Class<?> clazz = list.getFirst().getClass();
        EntityMeta meta = CACHE.computeIfAbsent(clazz, this::parseMeta);

        // 1️⃣ 先 truncate（单独事务）
        transactionTemplate.execute(status -> {
            jdbcTemplate.execute("truncate table " + meta.tableName);
            return null;
        });

        // 2️⃣ 分批提交
        int batchSize = 5000;
        for (int start = 0; start < list.size(); start += batchSize) {

            int end = Math.min(start + batchSize, list.size());
            List<T> subList = list.subList(start, end);

            transactionTemplate.execute(status -> {

                jdbcTemplate.batchUpdate(
                        meta.insertSql,
                        subList,
                        1000,
                        (ps, entity) -> {

                            for (int i = 0; i < meta.getters.size(); i++) {
                                Object value = null;
                                try {
                                    value = meta.getters.get(i).invoke(entity);
                                } catch (Throwable e) {
                                    throw new RuntimeException(e);
                                }
                                ps.setObject(i + 1, value);
                            }
                        }
                );

                return null;
            });
        }
    }

    /**
     * 解析元数据（构建 MethodHandle）
     */
    private EntityMeta parseMeta(Class<?> clazz) {

        Table table = clazz.getAnnotation(Table.class);
        if (table == null) {
            throw new RuntimeException(clazz.getName() + " missing @Table");
        }

        String tableName = table.name();

        List<Field> fields = Arrays.stream(clazz.getDeclaredFields())
                .filter(f -> f.isAnnotationPresent(Column.class))
                .sorted(Comparator.comparing(Field::getName)) // 🔹 保证顺序稳定
                .collect(Collectors.toList());

        if (fields.isEmpty()) {
            throw new RuntimeException(clazz.getName() + " no @Column fields");
        }

        List<String> columnList = new ArrayList<>();
        List<MethodHandle> getters = new ArrayList<>();

        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();

            for (Field field : fields) {

                Column column = field.getAnnotation(Column.class);
                columnList.add(column.name());

                field.setAccessible(true);

                MethodHandle getter =
                        lookup.unreflectGetter(field);

                getters.add(getter);
            }

        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }

        String placeholders = columnList.stream()
                .map(c -> "?")
                .collect(Collectors.joining(","));

        String insertSql =
                "insert into " + tableName +
                        " (" + String.join(",", columnList) + ")" +
                        " values (" + placeholders + ")";

        return new EntityMeta(tableName, insertSql, getters);
    }

    /**
     * 元数据结构
     */
    private static class EntityMeta {

        final String tableName;
        final String insertSql;
        final List<MethodHandle> getters;

        EntityMeta(String tableName,
                   String insertSql,
                   List<MethodHandle> getters) {

            this.tableName = tableName;
            this.insertSql = insertSql;
            this.getters = getters;
        }
    }

    public <T> void readAndBatchInsert(InputStream in,
                                       Class<T> clazz,
                                       int chunkSize) {

        if (chunkSize <= 0) {
            chunkSize = 500;
        }

        EntityMeta meta = parseMeta(clazz);

        List<T> buffer = new ArrayList<>(chunkSize);
        AtomicReference<List<String>> headerRef = new AtomicReference<>();

        int finalChunkSize = chunkSize;
        ExcelUtil.readBySax(in, 0, (sheetIndex, rowIndex, rowList) -> {

            // 1️⃣ 保存表头
            if (rowIndex == 0) {
                headerRef.set(
                        rowList.stream()
                                .map(obj -> obj == null ? null : obj.toString().trim())
                                .collect(Collectors.toList())
                );
                return;
            }

            List<String> headers = headerRef.get();

            // 2️⃣ 构造 Map<字段名,值>
            Map<String, Object> rowMap = new HashMap<>(headers.size());

            for (int i = 0; i < headers.size(); i++) {
                if (i < rowList.size()) {
                    rowMap.put(headers.get(i), rowList.get(i));
                }
            }

            // 3️⃣ Hutool 自动转实体
            T entity = BeanUtil.toBean(rowMap, clazz);

            buffer.add(entity);

            // 4️⃣ 分批入库
            if (buffer.size() >= finalChunkSize) {
                batchInsert(meta, buffer);
                buffer.clear();
            }
        });

        if (!buffer.isEmpty()) {
            batchInsert(meta, buffer);
        }
    }

    private <T> void batchInsert(EntityMeta meta, List<T> list) {

        transactionTemplate.executeWithoutResult(status -> {

            jdbcTemplate.batchUpdate(
                    meta.insertSql,
                    list,
                    list.size(),
                    (ps, entity) -> {


                        for (int i = 0; i < meta.getters.size(); i++) {
                            Object value = null;
                            try {
                                value = meta.getters.get(i).invoke(entity);
                            } catch (Throwable e) {
                                throw new RuntimeException(e);
                            }
                            ps.setObject(i + 1, value);
                        }
                    });
        });
    }
}


@Data
@Table(name = "user_info")
class UserInfo {

    @Column(name = "id")
    private Long id;

    @Column(name = "name")
    private String name;

    @Column(name = "age")
    private Integer age;

    @Column(name = "email")
    private String email;

    @Column(name = "create_time")
    private LocalDateTime createTime;
}