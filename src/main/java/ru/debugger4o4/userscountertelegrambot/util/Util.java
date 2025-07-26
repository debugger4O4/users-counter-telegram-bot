package ru.debugger4o4.userscountertelegrambot.util;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Util {

    public static List<String> getPercentValueFromData(List<Map<String, Object>> data, String value) {
        return data.stream()
                .map(map -> ((BigDecimal) map.get(value)).toString())
                .collect(Collectors.toList());
    }

    public static List<Integer> getAvgAgeFromData(List<Map<String, Object>> data) {
        return data.stream()
                .map(map -> ((BigDecimal)map.get("средний_возраст")).intValue())
                .collect(Collectors.toList());
    }
}
