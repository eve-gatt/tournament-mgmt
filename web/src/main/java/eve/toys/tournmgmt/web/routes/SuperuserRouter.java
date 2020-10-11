package eve.toys.tournmgmt.web.routes;

import eve.toys.tournmgmt.web.job.JobClient;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import toys.eve.tournmgmt.common.util.RenderHelper;
import toys.eve.tournmgmt.db.DbClient;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class SuperuserRouter {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneId.of("UTC"));

    private final RenderHelper render;
    private final JobClient jobClient;
    private final DbClient dbClient;
    private final Router router;

    public SuperuserRouter(Vertx vertx, RenderHelper render, JobClient jobClient, DbClient dbClient) {
        router = Router.router(vertx);
        this.render = render;
        this.jobClient = jobClient;
        this.dbClient = dbClient;
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
            case "ping-discord-rename-requests":
                jobClient.run(JobClient.JOB_PING_DISCORD_RENAME_REQUESTS, new JsonObject());
                break;
        }
        RenderHelper.doRedirect(ctx.response(), "/auth/superuser/home");
    }

    public static Router routes(Vertx vertx, RenderHelper render, JobClient jobClient, DbClient dbClient) {
        return new SuperuserRouter(vertx, render, jobClient, dbClient).router();
    }

    private Router router() {
        return router;
    }

}
