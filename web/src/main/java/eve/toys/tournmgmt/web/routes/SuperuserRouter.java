package eve.toys.tournmgmt.web.routes;

import eve.toys.tournmgmt.web.job.JobClient;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import toys.eve.tournmgmt.common.util.RenderHelper;

public class SuperuserRouter {

    private final EventBus eventBus;
    private final RenderHelper render;
    private final Router router;

    public SuperuserRouter(Vertx vertx, RenderHelper render) {
        router = Router.router(vertx);
        this.render = render;
        this.eventBus = vertx.eventBus();
        router.get("/home").handler(this::home);
        router.get("/job/:jobName").handler(this::job);
    }

    private void home(RoutingContext ctx) {
        render.renderPage(ctx, "/superuser/home", new JsonObject());
    }

    private void job(RoutingContext ctx) {
        String jobName = ctx.pathParam("jobName");
        switch (jobName) {
            case "check-alliance-membership":
                eventBus.publish(JobClient.JOB_CHECK_ALLIANCE_MEMBERSHIP, new JsonObject());
        }
        RenderHelper.doRedirect(ctx.response(), "/auth/superuser/home");
    }

    public static Router routes(Vertx vertx, RenderHelper render) {
        return new SuperuserRouter(vertx, render).router();
    }

    private Router router() {
        return router;
    }

}
