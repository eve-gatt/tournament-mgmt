package eve.toys.tournmgmt.web.esi;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.Tuple3;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.oauth2.AccessToken;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ShipSkillChecker {
    private static final List<Integer> SKILL_IDS = Arrays.asList(182, 183, 184, 1285, 1289, 1290);
    private static final List<Integer> SKILL_LEVELS = Arrays.asList(277, 278, 279, 1286, 1287, 1288);

    private final Esi esi;

    public ShipSkillChecker(Esi esi) {
        this.esi = esi;
    }

    public static Future<JsonObject> fetchShipDetails(Esi esi, JsonObject json) {
        if (json.getJsonArray("result") == null) {
            return Future.failedFuture(json.getString("inventory_type") + " doesn't exist");
        }
        return esi.fetchType(json.getJsonArray("result").getInteger(0));
    }

    public static Tuple2<List<JsonObject>, List<JsonObject>> fetchSkillRequirements(JsonObject shipType) {
        JsonArray attrs = shipType.getJsonObject("result").getJsonArray("dogma_attributes");
        List<JsonObject> skills = attrs.stream()
                .map(o -> (JsonObject) o)
                .filter(attr -> SKILL_IDS.contains(attr.getInteger("attribute_id")))
                .collect(Collectors.toList());
        List<JsonObject> levels = attrs.stream()
                .map(o -> (JsonObject) o)
                .filter(attr -> SKILL_LEVELS.contains(attr.getInteger("attribute_id")))
                .collect(Collectors.toList());
        return Tuple.of(skills, levels);
    }

    public static Future<Tuple3<List<JsonObject>, List<JsonObject>, Map<Integer, Integer>>> fetchUsersSkills(Esi esi, User user, Integer characterId, Tuple2<List<JsonObject>, List<JsonObject>> shipSkillRequirements) {
        List<Integer> requiredSkillIds = shipSkillRequirements._1().stream().map(skill -> skill.getInteger("value")).collect(Collectors.toList());
        return esi.fetchCharacterSkills((AccessToken) user, characterId)
                .map(json -> {
                    Map<Integer, Integer> relevantSkillIds = json.getJsonArray("skills").stream()
                            .map(o -> (JsonObject) o)
                            .filter(skill -> requiredSkillIds.contains(skill.getInteger("skill_id")))
                            .collect(Collectors.toMap(
                                    skill -> skill.getInteger("skill_id"),
                                    skill -> skill.getInteger("trained_skill_level")
                            ));
                    return shipSkillRequirements.append(relevantSkillIds);
                })
                .onFailure(Throwable::printStackTrace);
    }

    public static Future<JsonArray> resolveSkillNames(Esi esi, Tuple3<List<JsonObject>, List<JsonObject>, Map<Integer, Integer>> skills) {
        return CompositeFuture.all(skills._1().stream()
                .map(skillId -> esi.fetchType(skillId.getInteger("value")))
                .collect(Collectors.toList()))
                .compose(f -> Future.succeededFuture(new JsonArray(IntStream.range(0, skills._1().size())
                        .mapToObj(i -> {
                            JsonObject result = ((JsonObject) f.resultAt(i)).getJsonObject("result");
                            return new JsonObject()
                                    .put("skill", result.getString("name"))
                                    .put("level", skills._2().get(i).getInteger("value"))
                                    .put("toonLevel", skills._3().getOrDefault(result.getInteger("type_id"), 0));
                        })
                        .collect(Collectors.toList()))));
    }

    public Future<JsonArray> check(User user, Integer characterId, String shipName) {
        return esi.lookupShip(shipName)
                .compose(json -> fetchShipDetails(esi, json))
                .map(ShipSkillChecker::fetchSkillRequirements)
                .compose(skillsAndLevels -> fetchUsersSkills(esi, user, characterId, skillsAndLevels))
                .compose(tuple -> resolveSkillNames(esi, tuple));
    }

}
