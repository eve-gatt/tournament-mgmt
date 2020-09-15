package eve.toys.tournmgmt.web.routes;

import eve.toys.tournmgmt.web.esi.Esi;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.Tuple3;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.oauth2.AccessToken;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.WebClient;
import toys.eve.tournmgmt.common.util.RenderHelper;
import toys.eve.tournmgmt.db.DbClient;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static toys.eve.tournmgmt.common.util.RenderHelper.doRedirect;

public class ProfileRouter {

    private static final List<Integer> SKILL_IDS = Arrays.asList(182, 183, 184, 1285, 1289, 1290);
    private static final List<Integer> SKILL_LEVELS = Arrays.asList(277, 278, 279, 1286, 1287, 1288);
    private final WebClient webClient;
    private final RenderHelper render;
    private final Router router;
    private final DbClient dbClient;
    private Esi esi;

    public ProfileRouter(Vertx vertx, RenderHelper render, WebClient webClient, Esi esi, DbClient dbClient) {
        router = Router.router(vertx);
        this.render = render;
        this.webClient = webClient;
        this.esi = esi;
        this.dbClient = dbClient;

        router.get("/me").handler(this::me);
        router.get("/tournaments").handler(this::tournamentsJson);
        router.get("/shipcheck").handler(ctx -> doRedirect(ctx.response(), "/auth/profile/me"));
        router.post("/shipcheck").handler(this::shipCheck);
    }

    private void me(RoutingContext ctx) {
        render.renderPage(ctx, "/profile/me", new JsonObject());
    }

    private void tournamentsJson(RoutingContext ctx) {
        String characterName = ((JsonObject) ctx.data().get("character")).getString("characterName");
        dbClient.callDb(DbClient.DB_TOURNAMENTS_CHARACTER_CAN_VIEW, characterName)
                .onFailure(Throwable::printStackTrace)
                .onSuccess(result -> ctx.response().end(((JsonArray) result.body()).encode()));
    }

    private void shipCheck(RoutingContext ctx) {
        MultiMap form = ctx.request().formAttributes();
        String shipName = form.get("name");
        Integer characterId = ((JsonObject) ctx.data().get("character")).getInteger("characterId");
        esi.lookupShip(shipName)
                .compose(this::fetchShipDetails)
                .compose(this::fetchSkillRequirements)
                .compose(tuple -> fetchUsersSkills(ctx.user(), characterId, tuple))
                .compose(this::resolveSkillNames)
                .onSuccess(json -> render.renderPage(ctx, "/profile/me",
                        new JsonObject()
                                .put("shipname", shipName)
                                .put("shipcheck", json)))
                .onFailure(t -> {
                    t.printStackTrace();
                    doRedirect(ctx.response(), "/auth/profile/me");
                });
    }

    private Future<JsonObject> fetchShipDetails(JsonObject json) {
        if (json.getJsonArray("result") == null) {
            return Future.failedFuture(json.getString("inventory_type") + " doesn't exist");
        }
        return esi.fetchType(json.getJsonArray("result").getInteger(0));
    }

    private Future<Tuple2<List<JsonObject>, List<JsonObject>>> fetchSkillRequirements(JsonObject json) {
        JsonArray attrs = json.getJsonObject("result").getJsonArray("dogma_attributes");
        List<JsonObject> skills = attrs.stream()
                .map(o -> (JsonObject) o)
                .filter(attr -> SKILL_IDS.contains(attr.getInteger("attribute_id")))
                .collect(Collectors.toList());
        List<JsonObject> levels = attrs.stream()
                .map(o -> (JsonObject) o)
                .filter(attr -> SKILL_LEVELS.contains(attr.getInteger("attribute_id")))
                .collect(Collectors.toList());
        return Future.succeededFuture(Tuple.of(skills, levels));
    }

    private Future<Tuple3<List<JsonObject>, List<JsonObject>, Map<Integer, Integer>>> fetchUsersSkills(User user, Integer characterId, Tuple2<List<JsonObject>, List<JsonObject>> tuple) {
        List<Integer> skillIds = tuple._1().stream().map(skill -> skill.getInteger("value")).collect(Collectors.toList());
        return esi.getSkills((AccessToken) user, characterId)
                .compose(json -> {
                    Map<Integer, Integer> relevantSkillIds = json.getJsonArray("skills").stream()
                            .map(o -> (JsonObject) o)
                            .filter(skill -> skillIds.contains(skill.getInteger("skill_id")))
                            .collect(Collectors.toMap(
                                    skill -> skill.getInteger("skill_id"),
                                    skill -> skill.getInteger("trained_skill_level")
                            ));
                    return Future.succeededFuture(tuple.append(relevantSkillIds));
                })
                .onFailure(Throwable::printStackTrace);
    }

    private Future<JsonArray> resolveSkillNames(Tuple3<List<JsonObject>, List<JsonObject>, Map<Integer, Integer>> tuple) {
        return CompositeFuture.all(tuple._1().stream()
                .map(skillId -> esi.fetchType(skillId.getInteger("value")))
                .collect(Collectors.toList()))
                .compose(f -> Future.succeededFuture(new JsonArray(IntStream.range(0, tuple._1().size())
                        .mapToObj(i -> {
                            JsonObject result = ((JsonObject) f.resultAt(i)).getJsonObject("result");
                            return new JsonObject()
                                    .put("skill", result.getString("name"))
                                    .put("level", tuple._2().get(i).getInteger("value"))
                                    .put("toonLevel", tuple._3().getOrDefault(result.getInteger("type_id"), 0));
                        })
                        .collect(Collectors.toList()))));
    }

    public static Router routes(Vertx vertx, RenderHelper render, WebClient webClient, Esi esi, DbClient dbClient) {
        return new ProfileRouter(vertx, render, webClient, esi, dbClient).router();
    }

    private Router router() {
        return router;
    }
}
