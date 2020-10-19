package eve.toys.tournmgmt.web.routes;

import eve.toys.tournmgmt.web.esi.Esi;
import eve.toys.tournmgmt.web.job.JobClient;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import toys.eve.tournmgmt.common.util.RenderHelper;
import toys.eve.tournmgmt.db.DbClient;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.stream.Collectors;

public class SuperuserRouter {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneId.of("UTC"));

    private final RenderHelper render;
    private final JobClient jobClient;
    private final DbClient dbClient;
    private final Esi esi;
    private final Router router;

    public SuperuserRouter(Vertx vertx, RenderHelper render, JobClient jobClient, DbClient dbClient, Esi esi) {
        router = Router.router(vertx);
        this.render = render;
        this.jobClient = jobClient;
        this.dbClient = dbClient;
        this.esi = esi;
        router.get("/home").handler(this::home);
        router.get("/job/:jobName").handler(this::job);
        router.get("/ships").handler(this::ships);
    }

    public static Router routes(Vertx vertx, RenderHelper render, JobClient jobClient, DbClient dbClient, Esi esi) {
        return new SuperuserRouter(vertx, render, jobClient, dbClient, esi).router();
    }

    private void ships(RoutingContext ctx) {
        int[] groups = {25, 26, 27, 28, 29, 31, 237, 324, 358, 380, 381, 419, 420, 463, 513, 540, 541, 543, 830, 831, 832, 833, 834, 883, 893, 894, 898, 900, 902, 906, 941, 963, 1022, 1201, 1202, 1283, 1305, 1527, 1534, 1972, 2001};
        CompositeFuture.all(Arrays.stream(groups)
                .mapToObj(g -> esi.fetchGroup(g)
                        .compose(esiResult -> {
                            JsonObject result = esiResult.getJsonObject("result");
                            String groupId = String.valueOf(result.getInteger("group_id"));
                            String groupName = result.getString("name");
                            return CompositeFuture.all(result.getJsonArray("types").stream()
                                    .map(t -> (Integer) t)
                                    .map(esi::fetchType)
                                    .collect(Collectors.toList()))
                                    .map(f -> f.list().stream()
                                            .map(o -> (JsonObject) o)
                                            .map(er2 -> er2.getJsonObject("result"))
                                            .filter(er2 -> er2.getBoolean("published"))
                                            .map(er2 -> String.format("%s,%d,%s",
                                                    groupName.trim(),
                                                    er2.getInteger("type_id"),
                                                    er2.getString("name").trim()))
                                            .collect(Collectors.joining("\n")));
                        }))
                .collect(Collectors.toList()))
                .map(f -> f.list().stream()
                        .map(s -> (String) s)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.joining("\n")))
                .onFailure(ctx::fail)
                .onSuccess(out -> ctx.response().end(out));
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

    private Router router() {
        return router;
    }

}
