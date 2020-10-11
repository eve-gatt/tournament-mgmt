package eve.toys.tournmgmt.web.routes;

import eve.toys.tournmgmt.web.esi.Esi;
import eve.toys.tournmgmt.web.esi.ShipSkillChecker;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import toys.eve.tournmgmt.common.util.RenderHelper;
import toys.eve.tournmgmt.db.DbClient;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static toys.eve.tournmgmt.common.util.RenderHelper.doRedirect;

public class ProfileRouter {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneId.of("UTC"));

    private final RenderHelper render;
    private final Router router;
    private final DbClient dbClient;
    private final ShipSkillChecker shipSkillChecker;
    private Esi esi;

    public ProfileRouter(Vertx vertx, RenderHelper render, Esi esi, DbClient dbClient) {
        router = Router.router(vertx);
        this.render = render;
        this.esi = esi;
        this.dbClient = dbClient;
        shipSkillChecker = new ShipSkillChecker(esi);

        router.get("/me").handler(this::me);
        router.get("/tournaments").handler(this::tournamentsJson);
        router.get("/shipcheck").handler(ctx -> doRedirect(ctx.response(), "/auth/profile/me"));
        router.post("/shipcheck").handler(this::shipCheck);
        router.get("/name-in-use").handler(this::nameInUse);
        router.get("/name-in-use/:characterId/confirm").handler(this::nameInUseConfirm);
    }

    private void me(RoutingContext ctx) {
        checkNameInUseReports(ctx.data())
                .onFailure(ctx::fail)
                .onSuccess(v -> {
                    render.renderPage(ctx, "/profile/me", new JsonObject());
                });
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
        shipSkillChecker.check(ctx.user(), characterId, shipName)
                .onSuccess(json -> {
                    ctx.data().put("shipname", shipName);
                    ctx.data().put("shipcheck", json);
                    ctx.reroute(HttpMethod.GET, "/auth/profile/me");
                })
                .onFailure(t -> {
                    t.printStackTrace();
                    doRedirect(ctx.response(), "/auth/profile/me");
                });
    }

    private void nameInUse(RoutingContext ctx) {
        render.renderPage(ctx, "/profile/nameinuse", new JsonObject());
    }

    private void nameInUseConfirm(RoutingContext ctx) {
        int suppliedCharacterId = Integer.parseInt(ctx.request().getParam("characterId"));
        int loggedInCharacterId = ((JsonObject) ctx.data().get("character")).getInteger("characterId");
        String loggedInCharacterName = ((JsonObject) ctx.data().get("character")).getString("characterName");
        if (suppliedCharacterId != loggedInCharacterId) {
            ctx.fail(403);
        } else {
            dbClient.callDb(DbClient.DB_RECORD_NAME_IN_USE, loggedInCharacterName)
                    .onFailure(ctx::fail)
                    .onSuccess(msg -> {
                        doRedirect(ctx.response(), "/auth/profile/me");
                    });

        }
    }

    public Future<Void> checkNameInUseReports(Map<String, Object> data) {
        return Future.future(promise -> {
            String loggedInCharacterName = ((JsonObject) data.get("character")).getString("characterName");
            dbClient.callDb(DbClient.DB_CHECK_NAME_IN_USE_REPORTS, loggedInCharacterName)
                    .onFailure(promise::fail)
                    .onSuccess(result -> {
                        JsonArray reports = (JsonArray) result.body();
                        List<JsonObject> withFormattedDate = reports.stream()
                                .map(o -> (JsonObject) o)
                                .map(report -> {
                                    Instant resolvedAt = report.getInstant("resolved_at");
                                    if (resolvedAt != null) {
                                        report.put("resolved_at_formatted", DATE_FORMAT.format(resolvedAt));
                                    }
                                    return report.put("reported_at_formatted", DATE_FORMAT.format(report.getInstant("reported_at")));
                                })
                                .collect(Collectors.toList());
                        data.put("reports", new JsonArray(withFormattedDate));
                        if (withFormattedDate.stream()
                                .noneMatch(r -> r.getString("resolved_by") == null || r.getString("resolved_by").isEmpty())) {
                            data.put("canReportAgain", true);
                        }
                        promise.complete();
                    });
        });
    }

    public static Router routes(Vertx vertx, RenderHelper render, Esi esi, DbClient dbClient) {
        return new ProfileRouter(vertx, render, esi, dbClient).router();
    }

    private Router router() {
        return router;
    }
}
