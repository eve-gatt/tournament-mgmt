package eve.toys.tournmgmt.web.match;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.Map;
import java.util.stream.Collectors;

public class CompRule {
    public CompRule() {
    }

    private String rowToRuleClass(JsonObject row) {
        String clazz = row.getString("class");
        String overlay = row.getString("overlay");
        if (overlay.toLowerCase().contains("logistics cruiser") || overlay.toLowerCase().contains("support cruiser")) {
            clazz = "Logistics Cruiser";
        }
        if (overlay.toLowerCase().contains("logistics frigate")
            || overlay.toLowerCase().contains("support frigate")) {
            clazz = "Logistics Frigate";
        }
        return clazz;
    }

    Future<JsonArray> checkCompRules(JsonArray comp) {
        Map<String, Long> counts = comp.stream()
                .map(o -> (JsonObject) o)
                .collect(Collectors.groupingBy(this::rowToRuleClass, Collectors.counting()));
        counts
                .entrySet().stream()
                .filter(row -> {
                    if (row.getKey().equals("Logistics Cruiser")) {
                        return row.getValue() > 1;
                    }
                    if (row.getKey().equals("Logistics Frigate")) {
                        return row.getValue() > 2;
                    }
                    return row.getValue() > 3;
                })
                .forEach(invalidRow -> comp.stream()
                        .map(o -> (JsonObject) o)
                        .filter(row -> rowToRuleClass(row).equals(invalidRow.getKey()))
                        .forEach(row -> row.put("validComp", "Maximum " + invalidRow.getKey() + " allocation reached")));

        if (counts.getOrDefault("Logistics Cruiser", 0L) > 0
            && counts.getOrDefault("Logistics Frigate", 0L) > 0) {
            comp.stream()
                    .map(o -> (JsonObject) o)
                    .filter(row -> rowToRuleClass(row).contains("Logistics"))
                    .forEach(row -> row.put("validComp", "Maximum Logistics allocation reached"));
        }
        return Future.succeededFuture(comp);
    }
}
