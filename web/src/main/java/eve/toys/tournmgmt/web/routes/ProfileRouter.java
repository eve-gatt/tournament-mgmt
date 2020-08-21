package eve.toys.tournmgmt.web.routes;

import eve.toys.tournmgmt.web.Role;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import toys.eve.tournmgmt.common.util.RenderHelper;
import toys.eve.tournmgmt.db.DbClient;

import java.util.EnumSet;

public class ProfileRouter {

    private static EnumSet<Role> ORGANISER_ROLES = EnumSet.of(
            Role.canEdit,
            Role.canSearchPilot,
            Role.canManageTeams,
            Role.canManageRoles,
            Role.canManageBranding
    );

    private final EventBus eventBus;
    private Router router;
    private final RenderHelper render;

    public ProfileRouter(Vertx vertx, RenderHelper render) {
        router = Router.router(vertx);
        this.render = render;
        this.eventBus = vertx.eventBus();
        router.get("/me").handler(this::me);
        router.get("/tournaments").handler(this::tournaments);
    }

    private void tournaments(RoutingContext ctx) {
        String characterName = ((JsonObject) ctx.data().get("character")).getString("characterName");

        Future<Message<Object>> f1 = Future.future(promise -> {
            eventBus.request(DbClient.DB_FETCH_ORGANISED_TOURNAMENTS,
                    new JsonObject().put("organiser", characterName),
                    promise);
        });

        f1.onSuccess(msg -> {
            ctx.response().end(((JsonArray) msg.body()).stream()
                    .peek(t -> {
                        ORGANISER_ROLES.forEach(r -> ((JsonObject) t).put(r.name(), true));
                        ((JsonObject) t).put(Role.canManageTD.name(),
                                ((JsonObject) t).getBoolean("practice_on_td")
                                        || ((JsonObject) t).getBoolean("play_on_td"));
                    })
                    .collect(JsonArray::new, JsonArray::add, JsonArray::addAll)
                    .encode());
        }).onFailure(Throwable::printStackTrace);
    }

    private void me(RoutingContext ctx) {
        render.renderPage(ctx, "/profile/me", new JsonObject());
    }

    public static Router routes(Vertx vertx, RenderHelper render) {
        return new ProfileRouter(vertx, render).router();
    }

    private Router router() {
        return router;
    }
}
