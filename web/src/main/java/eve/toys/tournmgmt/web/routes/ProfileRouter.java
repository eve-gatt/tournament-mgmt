package eve.toys.tournmgmt.web.routes;

import eve.toys.tournmgmt.web.authn.AppRBAC;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import toys.eve.tournmgmt.common.util.RenderHelper;
import toys.eve.tournmgmt.db.DbClient;

import java.util.List;
import java.util.stream.Collectors;

public class ProfileRouter {

    private final EventBus eventBus;
    private final RenderHelper render;
    private final Router router;

    public ProfileRouter(Vertx vertx, RenderHelper render) {
        router = Router.router(vertx);
        this.render = render;
        this.eventBus = vertx.eventBus();
        router.get("/me").handler(this::me);
        router.get("/tournaments").handler(this::tournamentsJson);
    }

    private void me(RoutingContext ctx) {
        render.renderPage(ctx, "/profile/me", new JsonObject());
    }

    private void tournamentsJson(RoutingContext ctx) {
        String characterName = ((JsonObject) ctx.data().get("character")).getString("characterName");

        eventBus.request(DbClient.DB_FETCH_TOURNAMENTS,
                new JsonObject(),
                ar -> {
                    if (ar.failed()) {
                        ar.cause().printStackTrace();
                        ctx.fail(ar.cause());
                    } else {
                        List<Future> checks = ((JsonArray) ar.result().body()).stream()
                                .map(o -> (JsonObject) o)
                                .map(t -> AppRBAC.futureForTournamentPriv(ctx.user(), "organiser", t))
                                .collect(Collectors.toList());
                        CompositeFuture.all(checks)
                                .onFailure(Throwable::printStackTrace)
                                .onSuccess(f -> ctx.response().end(new JsonArray(f.list().stream()
                                        .map(o -> (JsonObject) o)
                                        .filter(t -> t.getBoolean("organiser")
                                                || AppRBAC.isSuperuser(characterName)
                                                || characterName.equals(t.getString("created_by")))
                                        .peek(AppRBAC::addPermissionsToTournament)
                                        .collect(Collectors.toList())).encode()));
                    }
                });
    }

    public static Router routes(Vertx vertx, RenderHelper render) {
        return new ProfileRouter(vertx, render).router();
    }

    private Router router() {
        return router;
    }
}
