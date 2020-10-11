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
                .handler(ctx -> {
                    JsonObject character = (JsonObject) ctx.data().get("character");
                    String characterName = character.getString("characterName");
                    boolean isSuperuser = character.getBoolean("isSuperuser");
                    if (!characterName.startsWith("CCP ") && !isSuperuser) {
                        ctx.fail(403);
                    } else {
                        ctx.next();
                    }
                })
                .handler(this::home);
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

    public static Router routes(Vertx vertx, RenderHelper render, DbClient dbClient) {
        return new CCPRouter(vertx, render, dbClient).router();
    }

    private Router router() {
        return router;
    }

}
