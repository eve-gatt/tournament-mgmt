package eve.toys.tournmgmt.web.routes;

import eve.toys.tournmgmt.web.job.JobClient;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import toys.eve.tournmgmt.common.util.RenderHelper;

public class SuperuserRouter {

    private final RenderHelper render;
    private final JobClient jobClient;
    private final Router router;

    public SuperuserRouter(Vertx vertx, RenderHelper render, JobClient jobClient) {
        router = Router.router(vertx);
        this.render = render;
        this.jobClient = jobClient;
        router.get("/home").handler(this::home);
        router.get("/job/:jobName").handler(this::job);
    }

    private void home(RoutingContext ctx) {
        render.renderPage(ctx, "/superuser/home", new JsonObject());
    }

    private void job(RoutingContext ctx) {
        String jobName = ctx.pathParam("jobName");
        switch (jobName) {
            case "check-captains-alliance-membership":
                jobClient.run(JobClient.JOB_CHECK_CAPTAIN_ALLIANCE_MEMBERSHIP, new JsonObject());
                break;
            case "check-pilots-alliance-membership":
                jobClient.run(JobClient.JOB_CHECK_PILOTS_ALLIANCE_MEMBERSHIP, new JsonObject());
                break;
            case "check-pilots-on-one-team":
                jobClient.run(JobClient.JOB_CHECK_PILOTS_ON_ONE_TEAM, new JsonObject());
                break;
        }
        RenderHelper.doRedirect(ctx.response(), "/auth/superuser/home");
    }

    public static Router routes(Vertx vertx, RenderHelper render, JobClient jobClient) {
        return new SuperuserRouter(vertx, render, jobClient).router();
    }

    private Router router() {
        return router;
    }

}
