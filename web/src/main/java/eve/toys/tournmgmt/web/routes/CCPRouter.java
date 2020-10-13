package eve.toys.tournmgmt.web.routes;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import toys.eve.tournmgmt.common.util.RenderHelper;
import toys.eve.tournmgmt.db.DbClient;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;

public class CCPRouter {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneId.of("UTC"));

    private final RenderHelper render;
    private final DbClient dbClient;
    private final Router router;

    public CCPRouter(Vertx vertx, RenderHelper render, DbClient dbClient) {
        router = Router.router(vertx);
        this.render = render;
        this.dbClient = dbClient;
        router.get("/home")
                .handler(CCPRouter::isCCPOrSuperuser)
                .handler(this::home);
        router.get("/rename/:uuid/resolve")
                .handler(CCPRouter::isCCPOrSuperuser)
                .handler(this::toggleResolved);
    }

    private static void isCCPOrSuperuser(RoutingContext ctx) {
        JsonObject character = (JsonObject) ctx.data().get("character");
        String characterName = character.getString("characterName");
        boolean isSuperuser = character.getBoolean("isSuperuser");
        if (!characterName.startsWith("CCP ") && !isSuperuser) {
            ctx.fail(403);
        } else {
            ctx.next();
        }
    }

    public static Router routes(Vertx vertx, RenderHelper render, DbClient dbClient) {
        return new CCPRouter(vertx, render, dbClient).router();
    }

    private void toggleResolved(RoutingContext ctx) {
        String uuid = ctx.request().getParam("uuid");
        String resolvedBy = ((JsonObject) ctx.data().get("character")).getString("characterName");
        dbClient.callDb(DbClient.DB_TOGGLE_RESOLVED, new JsonObject().put("uuid", uuid).put("resolvedBy", resolvedBy))
                .onFailure(ctx::fail)
                .onSuccess(v -> RenderHelper.doRedirect(ctx.response(), "/auth/ccp/home"));
    }

    private void home(RoutingContext ctx) {
        dbClient.callDb(DbClient.DB_PILOT_NAMES_IN_USE, new JsonObject())
                .onFailure(ctx::fail)
                .onSuccess(msg -> {
                    JsonArray reports = new JsonArray(((JsonArray) msg.body()).stream()
                            .map(o -> (JsonObject) o)
                            .map(r -> r.put("reportedAtFormatted", DATE_FORMAT.format(r.getInstant("reported_at"))))
                            .collect(Collectors.toList()));
                    render.renderPage(ctx, "/superuser/ccp",
                            new JsonObject()
                                    .put("reports", reports));
                });
    }

    private Router router() {
        return router;
    }

}
