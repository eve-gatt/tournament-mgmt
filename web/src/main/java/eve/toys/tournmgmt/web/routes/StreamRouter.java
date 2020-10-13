package eve.toys.tournmgmt.web.routes;

import eve.toys.tournmgmt.web.esi.Esi;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import toys.eve.tournmgmt.common.util.RenderHelper;
import toys.eve.tournmgmt.db.DbClient;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class StreamRouter {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneId.of("UTC"));

    private final RenderHelper render;
    private final DbClient dbClient;
    private final Esi esi;
    private final Router router;

    public StreamRouter(Vertx vertx, RenderHelper render, DbClient dbClient, Esi esi) {
        router = Router.router(vertx);
        this.render = render;
        this.dbClient = dbClient;
        this.esi = esi;
        router.get("/")
//                .handler(StreamRouter::canStream)
                .handler(this::home);
    }

    private static void canStream(RoutingContext ctx) {
        JsonObject character = (JsonObject) ctx.data().get("character");
        String characterName = character.getString("characterName");
        boolean isSuperuser = character.getBoolean("isSuperuser");
        if (!characterName.equalsIgnoreCase("Bei ArtJay") && !isSuperuser) {
            ctx.fail(403);
        } else {
            ctx.next();
        }
    }

    public static Router routes(Vertx vertx, RenderHelper render, DbClient dbClient, Esi esi) {
        return new StreamRouter(vertx, render, dbClient, esi).router();
    }

    private void home(RoutingContext ctx) {
        render.renderPage(ctx, "/stream/home", new JsonObject());
    }

    private Router router() {
        return router;
    }

}
