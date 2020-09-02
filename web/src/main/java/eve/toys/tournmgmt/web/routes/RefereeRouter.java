package eve.toys.tournmgmt.web.routes;

import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.ext.web.Router;
import toys.eve.tournmgmt.common.util.RenderHelper;

public class RefereeRouter {

    private final EventBus eventBus;
    private final RenderHelper render;
    private final Router router;

    public RefereeRouter(Vertx vertx, RenderHelper render) {
        router = Router.router(vertx);
        this.render = render;
        this.eventBus = vertx.eventBus();
    }

    public static Router routes(Vertx vertx, RenderHelper render) {
        return new RefereeRouter(vertx, render).router();
    }

    private Router router() {
        return router;
    }

}
