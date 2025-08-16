package org.example.pult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RowDataDynamic {

    private final LinkedHashMap<String, String> values;

    public RowDataDynamic() {
        this.values = new LinkedHashMap<>();
    }

    public RowDataDynamic(Map<String, String> initial) {
        this.values = new LinkedHashMap<>();
        if (initial != null) {
            for (Map.Entry<String, String> e : initial.entrySet()) {
                this.values.put(e.getKey(), e.getValue());
            }
        }
    }

    public void put(String key, String value) {
        values.put(key, value);
    }

    public String get(String key) {
        return values.get(key);
    }

    /** Заголовки/поля строки в исходном порядке. */
    public List<String> getKeys() {
        return new ArrayList<>(values.keySet());
    }

    /** Значения строки в порядке заголовков. */
    public List<String> getAllProperties() {
        return new ArrayList<>(values.values());
    }

    /** Вся карта значений (копия). */
    public Map<String, String> asMap() {
        return new LinkedHashMap<>(values);
    }

    @Override
    public String toString() {
        return "RowDataDynamic" + values;
    }
}
