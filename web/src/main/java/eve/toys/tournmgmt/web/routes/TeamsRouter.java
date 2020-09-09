package eve.toys.tournmgmt.web.routes;

import eve.toys.tournmgmt.web.esi.Esi;
import eve.toys.tournmgmt.web.esi.ValidatePilotNames;
import eve.toys.tournmgmt.web.job.JobClient;
import eve.toys.tournmgmt.web.tsv.TSV;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.api.RequestParameters;
import io.vertx.ext.web.client.WebClient;
import toys.eve.tournmgmt.common.util.RenderHelper;
import toys.eve.tournmgmt.db.DbClient;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static toys.eve.tournmgmt.common.util.RenderHelper.*;

public class TeamsRouter {

    private final EventBus eventBus;
    private final RenderHelper render;
    private final Router router;
    private final WebClient webClient;
    private final Esi esi;

    public TeamsRouter(Vertx vertx, RenderHelper render, WebClient webClient, Esi esi) {
        router = Router.router(vertx);
        this.render = render;
        this.eventBus = vertx.eventBus();
        this.webClient = webClient;
        this.esi = esi;

        router.route("/:tournamentUuid/teams/:teamUuid/*").handler(this::loadTeam);
        router.get("/:tournamentUuid/teams").handler(this::manage);
        router.get("/:tournamentUuid/teams/data").handler(this::teamsData);
        router.get("/:tournamentUuid/teams/:teamUuid/edit").handler(this::editTeam);
        router.get("/:tournamentUuid/teams/:teamUuid/remove").handler(this::removeTeam);
        router.get("/:tournamentUuid/teams/:teamUuid/remove/confirm").handler(this::removeTeamConfirm);
        router.get("/:tournamentUuid/teams/import").handler(this::importTeams);
        router.post("/:tournamentUuid/teams/import")
                .handler(TSV.VALIDATOR)
                .handler(this::handleImportTeams)
                .failureHandler(this::handleImportFail);
        router.get("/:tournamentUuid/teams/:teamUuid/add-members").handler(this::addMembers);
        router.post("/:tournamentUuid/teams/:teamUuid/add-members")
                .handler(TSV.VALIDATOR)
                .handler(this::handleAddMembers)
                .failureHandler(this::handleAddMembersFail);
        router.get("/:tournamentUuid/teams/:teamUuid/members/data").handler(this::membersData);
        router.get("/:tournamentUuid/teams/:teamUuid/lock-team").handler(this::lockTeam);
        router.get("/:tournamentUuid/teams/:teamUuid/lock-team/confirm").handler(this::lockTeamConfirm);
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
                        doRedirect(ctx.response(), teamUrl(ctx, "/remove"));
                        return;
                    }
                    doRedirect(ctx.response(), tournamentUrl(ctx, "/teams"));
                });
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

    private void handleImportTeams(RoutingContext ctx) {
        RequestParameters params = ctx.get("parsedParameters");
        TSV tsv = new TSV(params.formParameter("tsv").getString(), 2);

        List<Future> searches = tsv.stream()
                .flatMap(row -> Stream.of(
                        esi.lookupAlliance(webClient, row.getCol(0)),
                        esi.lookupCharacter(webClient, row.getCol(1))))
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
                                    result += r.getString("alliance") + " is not a valid alliance\n";
                                }
                            }
                            if ("character".equals(r.getString("category"))) {
                                if (r.getJsonArray("result") == null) {
                                    result += r.getString("character") + " is not a valid character name\n";
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
                            doRedirect(ctx.response(), tournamentUrl(ctx, "/teams"));
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
            doRedirect(ctx.response(), tournamentUrl(ctx, "/teams/import"));
        });
    }

    private void handleImportFail(RoutingContext ctx) {
        Throwable failure = ctx.failure();
        failure.printStackTrace();
        doRedirect(ctx.response(), tournamentUrl(ctx, "/teams/import"));
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

    private void handleAddMembers(RoutingContext ctx) {
        RequestParameters params = ctx.get("parsedParameters");
        TSV tsv = new TSV(params.formParameter("tsv").getString(), 1);

        new ValidatePilotNames(webClient, esi).validate(tsv, ar -> {
            if (ar.failed()) {
                ar.cause().printStackTrace();
                doRedirect(ctx.response(), teamUrl(ctx, "/add-members"));
                return;
            }
            if (ar.result().isEmpty()) {
                // TODO: check if too many members after import
                // TODO: check members aren't in other teams
                eventBus.request(DbClient.DB_WRITE_TEAM_MEMBERS_TSV,
                        new JsonObject()
                                .put("tsv", tsv.json())
                                .put("addedBy", ((JsonObject) ctx.data().get("character")).getString("characterName"))
                                .put("uuid", ctx.request().getParam("teamUuid")),
                        ar2 -> {
                            if (ar2.failed()) {
                                ar2.cause().printStackTrace();
                                render.renderPage(ctx,
                                        "/teams/addmembers",
                                        new JsonObject()
                                                .put("placeholder", "Please fix the errors and paste in the revised data.")
                                                .put("tsv", tsv.text())
                                                .put("errors", ar2.cause().getMessage()));
                                return;
                            }
                            doRedirect(ctx.response(), teamUrl(ctx, "/edit"));
                        });
            } else {
                render.renderPage(ctx,
                        "/teams/addmembers",
                        new JsonObject()
                                .put("placeholder", "Please fix the errors and paste in the revised data.")
                                .put("tsv", tsv.text())
                                .put("errors", ar.result()));
            }
        });
    }

    private void handleAddMembersFail(RoutingContext ctx) {
        Throwable failure = ctx.failure();
        failure.printStackTrace();
        doRedirect(ctx.response(), teamUrl(ctx, "/add-members"));
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

    private void lockTeam(RoutingContext ctx) {
        render.renderPage(ctx, "/teams/lockteam", new JsonObject());
    }

    private void lockTeamConfirm(RoutingContext ctx) {
        eventBus.request(DbClient.DB_LOCK_TEAM_BY_UUID,
                ctx.request().getParam("teamUuid"),
                ar -> {
                    if (ar.failed()) {
                        ar.cause().printStackTrace();
                        doRedirect(ctx.response(), teamUrl(ctx, "/lock-team"));
                        return;
                    }
                    doRedirect(ctx.response(), tournamentUrl(ctx, "/teams"));
                });
    }

    public static Router routes(Vertx vertx, RenderHelper render, WebClient webClient, Esi esi) {
        return new TeamsRouter(vertx, render, webClient, esi).router();
    }

    private Router router() {
        return router;
    }

}
