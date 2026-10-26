package com.kewei.parts.utils;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;
import java.math.BigDecimal;
import java.util.*;

/** 本项目v2签名格式：UTF-8、对象键排序、日期使用毫秒时间戳，数组保持原顺序。 */
public final class CanonicalJson {
    private CanonicalJson() {}

    public static String write(Object value) {
        return JSON.toJSONString(normalize(value), SerializerFeature.MapSortField,
                SerializerFeature.WriteMapNullValue, SerializerFeature.WriteBigDecimalAsPlain);
    }

    private static Object normalize(Object value) {
        if (value == null || value instanceof String || value instanceof Boolean) return value;
        if (value instanceof Date) return (((Date) value).getTime() / 1000) * 1000;
        if (value instanceof Number) return new BigDecimal(value.toString()).stripTrailingZeros();
        if (value instanceof Map) {
            Map<String, Object> result = new TreeMap<>();
            ((Map<?, ?>) value).forEach((key, item) -> result.put(String.valueOf(key), normalize(item)));
            return result;
        }
        if (value instanceof Iterable) {
            List<Object> result = new ArrayList<>();
            for (Object item : (Iterable<?>) value) result.add(normalize(item));
            return result;
        }
        return normalize(JSON.toJSON(value));
    }
}
