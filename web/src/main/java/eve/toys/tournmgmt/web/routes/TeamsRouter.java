package eve.toys.tournmgmt.web.routes;

import eve.toys.tournmgmt.web.esi.Esi;
import eve.toys.tournmgmt.web.job.JobClient;
import eve.toys.tournmgmt.web.tsv.TSV;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.oauth2.AccessToken;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.api.RequestParameters;
import io.vertx.ext.web.api.validation.HTTPRequestValidationHandler;
import io.vertx.ext.web.api.validation.ParameterTypeValidator;
import io.vertx.ext.web.client.WebClient;
import toys.eve.tournmgmt.common.util.RenderHelper;
import toys.eve.tournmgmt.db.DbClient;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TeamsRouter {

    private final EventBus eventBus;
    private final RenderHelper render;
    private final Router router;
    private final WebClient webClient;

    public TeamsRouter(Vertx vertx, RenderHelper render, WebClient webClient) {
        router = Router.router(vertx);
        this.render = render;
        this.eventBus = vertx.eventBus();
        this.webClient = webClient;

        HTTPRequestValidationHandler tsvValidator = HTTPRequestValidationHandler.create()
                .addFormParamWithCustomTypeValidator("tsv",
                        ParameterTypeValidator.createStringTypeValidator(null, 7, null, null),
                        true,
                        false);

        router.route("/:tournamentUuid/teams/:teamUuid/*").handler(this::loadTeam);
        router.get("/:tournamentUuid/teams").handler(this::manage);
        router.get("/:tournamentUuid/teams/data").handler(this::teamsData);
        router.get("/:tournamentUuid/teams/:teamUuid/edit").handler(this::editTeam);
        router.get("/:tournamentUuid/teams/:teamUuid/remove").handler(this::removeTeam);
        router.get("/:tournamentUuid/teams/:teamUuid/remove/confirm").handler(this::removeTeamConfirm);
        router.get("/:tournamentUuid/teams/import").handler(this::importTeams);
        router.post("/:tournamentUuid/teams/import")
                .handler(tsvValidator)
                .handler(this::handleImportTeams)
                .failureHandler(this::handleImportFail);
        router.get("/:tournamentUuid/teams/:teamUuid/add-members").handler(this::addMembers);
        router.post("/:tournamentUuid/teams/:teamUuid/add-members")
                .handler(tsvValidator)
                .handler(this::handleAddMembers)
                .failureHandler(this::handleAddMembersFail);
        router.get("/:tournamentUuid/teams/:teamUuid/members/data").handler(this::membersData);
        router.get("/:tournamentUuid/teams/:teamUuid/lock-team").handler(this::lockTeam);
        router.get("/:tournamentUuid/teams/:teamUuid/lock-team/confirm").handler(this::lockTeamConfirm);
    }

    private void handleImportTeams(RoutingContext ctx) {
        RequestParameters params = ctx.get("parsedParameters");
        TSV tsv = new TSV(params.formParameter("tsv").getString(), 2);
        AccessToken token = (AccessToken) ctx.user();

        List<Future> searches = tsv.stream()
                .flatMap(row -> Stream.of(
                        Esi.lookupAlliance(webClient, row.getCol(0)),
                        Esi.checkCharacter(webClient, row.getCol(1))))
                .collect(Collectors.toList());

        CompositeFuture.all(searches).onSuccess(f -> {
            String msg = f.list().stream()
                    .map(o -> (JsonObject) o)
                    .flatMap(r -> {
                        String result = "";
                        if (r.containsKey("error")) {
                            result += r.getString("error") + "\n";
                        } else {
                            if ("alliance".equals(r.getString("category"))) {
                                if (r.getJsonArray("result") == null) {
                                    result += r.getString("name") + " is not a valid alliance\n";
                                }
                            }
                            if ("character".equals(r.getString("category"))) {
                                if (r.getJsonArray("result") == null) {
                                    result += r.getString("name") + " is not a valid character name\n";
                                }
                            }
                        }
                        return result.isEmpty() ? Stream.empty() : Stream.of(result);
                    })
                    .collect(Collectors.joining());

            if (msg.isEmpty()) {
                eventBus.request(DbClient.DB_WRITE_TEAM_TSV,
                        new JsonObject()
                                .put("tsv", tsv.json())
                                .put("createdBy",
                                        ((JsonObject) ctx.data().get("character")).getString("characterName"))
                                .put("uuid", ctx.request().getParam("tournamentUuid")),
                        ar -> {
                            if (ar.failed()) {
                                String error = ar.cause().getMessage();
                                if (error.contains("duplicate key value")) {
                                    error = "This data contains a team that is already in this tournament.";
                                } else {
                                    ar.cause().printStackTrace();
                                }
                                render.renderPage(ctx,
                                        "/teams/import",
                                        new JsonObject()
                                                .put("placeholder", "Please fix the errors and paste in the revised data.")
                                                .put("tsv", tsv.text())
                                                .put("errors", error));
                                return;
                            }
                            eventBus.publish(JobClient.JOB_CHECK_ALLIANCE_MEMBERSHIP, new JsonObject());
                            RenderHelper.doRedirect(ctx.response(), tournamentUrl(ctx, "/teams"));
                        });
            } else {
                render.renderPage(ctx,
                        "/teams/import",
                        new JsonObject()
                                .put("placeholder", "Please fix the errors and paste in the revised data.")
                                .put("tsv", tsv.text())
                                .put("errors", msg));
            }
        }).onFailure(throwable -> {
            throwable.printStackTrace();
            RenderHelper.doRedirect(ctx.response(), tournamentUrl(ctx, "/teams/import"));
        });
    }

    private Router router() {
        return router;
    }

    private void handleAddMembers(RoutingContext ctx) {
        RequestParameters params = ctx.get("parsedParameters");
        TSV tsv = new TSV(params.formParameter("tsv").getString(), 1);
        AccessToken token = (AccessToken) ctx.user();

        List<Future> searches = tsv.stream()
                .map(row -> Esi.checkCharacter(webClient, row.getCol(0)))
                .collect(Collectors.toList());

        CompositeFuture.all(searches).onSuccess(f -> {
            String msg = f.list().stream()
                    .map(o -> (JsonObject) o)
                    .flatMap(r -> {
                        String result = "";
                        if (r.getJsonArray("result") == null) {
                            result += r.getString("name") + " is not a valid character name";
                        }
                        return result.isEmpty() ? Stream.empty() : Stream.of(result);
                    }).collect(Collectors.joining("\n"))
                    .trim();
            if (msg.isEmpty()) {
                // TODO: check if too many members after import
                // TODO: check members aren't in other teams
                eventBus.request(DbClient.DB_WRITE_TEAM_MEMBERS_TSV,
                        new JsonObject()
                                .put("tsv", tsv.json())
                                .put("addedBy", ((JsonObject) ctx.data().get("character")).getString("characterName"))
                                .put("uuid", ctx.request().getParam("teamUuid")),
                        ar -> {
                            if (ar.failed()) {
                                ar.cause().printStackTrace();
                                render.renderPage(ctx,
                                        "/teams/addmembers",
                                        new JsonObject()
                                                .put("placeholder", "Please fix the errors and paste in the revised data.")
                                                .put("tsv", tsv.text())
                                                .put("errors", ar.cause().getMessage()));
                                return;
                            }
                            RenderHelper.doRedirect(ctx.response(), teamUrl(ctx, "/edit"));
                        });
            } else {
                render.renderPage(ctx,
                        "/teams/addmembers",
                        new JsonObject()
                                .put("placeholder", "Please fix the errors and paste in the revised data.")
                                .put("tsv", tsv.text())
                                .put("errors", msg));
            }
        }).onFailure(throwable -> {
            throwable.printStackTrace();
            RenderHelper.doRedirect(ctx.response(), teamUrl(ctx, "/add-members"));
        });
    }

    private void membersData(RoutingContext ctx) {
        eventBus.request(DbClient.DB_MEMBERS_BY_TEAM,
                ctx.request().getParam("teamUuid"),
                ar -> {
                    if (ar.failed()) {
                        ctx.fail(ar.cause());
                        return;
                    }
                    ctx.response().end(((JsonArray) ar.result().body()).encode());
                });
    }

    private void loadTeam(RoutingContext ctx) {
        eventBus.request(DbClient.DB_TEAM_BY_UUID,
                ctx.request().getParam("teamUuid"),
                ar -> {
                    if (ar.failed()) {
                        ctx.fail(ar.cause());
                        return;
                    }
                    ctx.data().put("team", ar.result().body());
                    ctx.next();
                });
    }

    private void manage(RoutingContext ctx) {
        render.renderPage(ctx, "/teams/manage", new JsonObject());
    }

    private void teamsData(RoutingContext ctx) {
        eventBus.request(DbClient.DB_TEAMS_BY_TOURNAMENT,
                new JsonObject().put("uuid", ctx.request().getParam("tournamentUuid")),
                ar -> {
                    if (ar.failed()) {
                        ctx.fail(ar.cause());
                        return;
                    }
                    ctx.response().end(((JsonArray) ar.result().body()).encode());
                });
    }

    private void editTeam(RoutingContext ctx) {
        render.renderPage(ctx, "/teams/edit", new JsonObject());
    }

    private void removeTeam(RoutingContext ctx) {
        render.renderPage(ctx, "/teams/remove", new JsonObject());
    }

    private void removeTeamConfirm(RoutingContext ctx) {
        eventBus.request(DbClient.DB_DELETE_TEAM_BY_UUID,
                ctx.request().getParam("teamUuid"),
                ar -> {
                    if (ar.failed()) {
                        ar.cause().printStackTrace();
                        RenderHelper.doRedirect(ctx.response(), teamUrl(ctx, "/remove"));
                        return;
                    }
                    RenderHelper.doRedirect(ctx.response(), tournamentUrl(ctx, "/teams"));
                });
    }

    private void lockTeam(RoutingContext ctx) {
        render.renderPage(ctx, "/teams/lockteam", new JsonObject());
    }

    private void importTeams(RoutingContext ctx) {
        render.renderPage(ctx,
                "/teams/import",
                new JsonObject()
                        .put("tsv", "")
                        .put("placeholder",
                                "Paste values straight from spreadsheet as two column, name and captain, e.g. \n" +
                                        "The Tuskers\tMira Chieve\n" +
                                        "Big Alliancia\tCaptain Jack\n" +
                                        "\n" +
                                        "Tab-separated values are the default format when copy and pasting a range from " +
                                        "a spreadsheet. CSV also works."));
    }

    public static Router routes(Vertx vertx, RenderHelper render, WebClient webClient) {
        return new TeamsRouter(vertx, render, webClient).router();
    }

    private void handleImportFail(RoutingContext ctx) {
        Throwable failure = ctx.failure();
        failure.printStackTrace();
        RenderHelper.doRedirect(ctx.response(), tournamentUrl(ctx, "/teams/import"));
    }

    private void addMembers(RoutingContext ctx) {
        render.renderPage(ctx,
                "/teams/addmembers",
                new JsonObject()
                        .put("tsv", "")
                        .put("placeholder",
                                "Paste values straight from spreadsheet as a single column of pilot names, e.g.\n\n" +
                                        "Jack Spratt\n" +
                                        "John Pilot\n" +
                                        "Josie Tackle\n"));
    }

    private void lockTeamConfirm(RoutingContext ctx) {
        eventBus.request(DbClient.DB_LOCK_TEAM_BY_UUID,
                ctx.request().getParam("teamUuid"),
                ar -> {
                    if (ar.failed()) {
                        ar.cause().printStackTrace();
                        RenderHelper.doRedirect(ctx.response(), teamUrl(ctx, "/lock-team"));
                        return;
                    }
                    RenderHelper.doRedirect(ctx.response(), tournamentUrl(ctx, "/teams"));
                });
    }

    private void handleAddMembersFail(RoutingContext ctx) {
        Throwable failure = ctx.failure();
        failure.printStackTrace();
        RenderHelper.doRedirect(ctx.response(), teamUrl(ctx, "/add-members"));
    }

    private String teamUrl(RoutingContext ctx, String suffix) {
        return tournamentUrl(ctx, "/teams/" + ctx.request().getParam("teamUuid") + suffix);
    }

    private String tournamentUrl(RoutingContext ctx, String suffix) {
        return "/auth/tournament/"
                + ctx.request().getParam("tournamentUuid")
                + suffix;
    }
}
