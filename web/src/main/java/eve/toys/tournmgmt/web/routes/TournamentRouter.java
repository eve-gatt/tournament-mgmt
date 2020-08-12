package eve.toys.tournmgmt.web.routes;


import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import toys.eve.tournmgmt.common.util.RenderHelper;

public class TournamentRouter {

    private final Router router;
    private final RenderHelper render;

    public TournamentRouter(Vertx vertx, RenderHelper render) {
        router = Router.router(vertx);
        this.render = render;
        router.get("/create").handler(this::create);
    }

    private void create(RoutingContext ctx) {
        render.renderPage(ctx, "/tournament/create", new JsonObject());
    }

    public static Router routes(Vertx vertx, RenderHelper render) {
        return new TournamentRouter(vertx, render).router();
    }

    private Router router() {
        return router;
    }
}
