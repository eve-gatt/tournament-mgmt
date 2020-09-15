package eve.toys.tournmgmt.web.routes;

import eve.toys.tournmgmt.web.AppStreamHelpers;
import eve.toys.tournmgmt.web.authn.AppRBAC;
import eve.toys.tournmgmt.web.authn.AuthnRule;
import eve.toys.tournmgmt.web.authn.Role;
import eve.toys.tournmgmt.web.esi.Esi;
import eve.toys.tournmgmt.web.esi.ValidatePilotNames;
import eve.toys.tournmgmt.web.job.JobClient;
import eve.toys.tournmgmt.web.tsv.TSV;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.api.RequestParameters;
import io.vertx.ext.web.api.validation.ValidationException;
import io.vertx.ext.web.client.WebClient;
import toys.eve.tournmgmt.common.util.RenderHelper;
import toys.eve.tournmgmt.db.DbClient;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static toys.eve.tournmgmt.common.util.RenderHelper.*;

public class TeamsRouter {

    private static final Pattern DUPE_REGEX = Pattern.compile(".*Detail: Key \\([^=]+=\\(([^,]+), ([^\\)]+)\\) already exists\\.");

    private final RenderHelper render;
    private final Router router;
    private final WebClient webClient;
    private final Esi esi;
    private final DbClient dbClient;
    private final JobClient jobClient;

    public TeamsRouter(Vertx vertx, RenderHelper render, WebClient webClient, Esi esi, DbClient dbClient, JobClient jobClient) {
        router = Router.router(vertx);
        this.render = render;
        this.webClient = webClient;
        this.esi = esi;
        this.dbClient = dbClient;
        this.jobClient = jobClient;

        AuthnRule isOrganiser = AuthnRule.create().role(Role.ORGANISER);
        AuthnRule isOrganiserOrCaptain = AuthnRule.create().role(Role.ORGANISER).isCaptain();
        AuthnRule isOrganiserOrCaptainOrPilotOrReferee = AuthnRule.create().role(Role.ORGANISER, Role.REFEREE).isCaptain().isPilot();

        router.route("/:tournamentUuid/teams/:teamUuid/*").handler(this::loadTeam);
        router.get("/:tournamentUuid/teams").handler(this::manage);
        router.get("/:tournamentUuid/teams/data").handler(this::teamsData);
        router.get("/:tournamentUuid/teams/:teamUuid/edit")
                .handler(ctx -> AppRBAC.tournamentAuthn(ctx, isOrganiserOrCaptainOrPilotOrReferee))
                .handler(this::editTeam);
        router.get("/:tournamentUuid/teams/:teamUuid/remove")
                .handler(ctx -> AppRBAC.tournamentAuthn(ctx, isOrganiser))
                .handler(this::removeTeam);
        router.get("/:tournamentUuid/teams/:teamUuid/remove/confirm")
                .handler(ctx -> AppRBAC.tournamentAuthn(ctx, isOrganiser))
                .handler(this::removeTeamConfirm);
        router.get("/:tournamentUuid/teams/import")
                .handler(ctx -> AppRBAC.tournamentAuthn(ctx, isOrganiser))
                .handler(this::importTeams);
        router.post("/:tournamentUuid/teams/import")
                .handler(ctx -> AppRBAC.tournamentAuthn(ctx, isOrganiser))
                .handler(TSV.VALIDATOR)
                .handler(this::handleImportTeams)
                .failureHandler(this::handleImportFail);
        router.get("/:tournamentUuid/teams/:teamUuid/add-members")
                .handler(ctx -> AppRBAC.teamAuthn(ctx, isOrganiserOrCaptain))
                .handler(this::addMembers);
        router.post("/:tournamentUuid/teams/:teamUuid/add-members")
                .handler(ctx -> AppRBAC.teamAuthn(ctx, isOrganiserOrCaptain))
                .handler(TSV.VALIDATOR)
                .handler(this::handleAddMembers)
                .failureHandler(this::handleAddMembersFail);
        router.get("/:tournamentUuid/teams/:teamUuid/members/data").handler(this::membersData);
        router.get("/:tournamentUuid/teams/:teamUuid/lock-team")
                .handler(ctx -> AppRBAC.teamAuthn(ctx, isOrganiserOrCaptain))
                .handler(this::lockTeam);
        router.get("/:tournamentUuid/teams/:teamUuid/lock-team/confirm")
                .handler(ctx -> AppRBAC.teamAuthn(ctx, isOrganiserOrCaptain))
                .handler(this::lockTeamConfirm);
        router.route("/:tournamentUuid/teams/:teamUuid/kick/:pilotUuid").handler(this::loadPilot);
        router.get("/:tournamentUuid/teams/:teamUuid/kick/:pilotUuid")
                .handler(ctx -> AppRBAC.teamAuthn(ctx, isOrganiserOrCaptain))
                .handler(this::kickMember);
        router.get("/:tournamentUuid/teams/:teamUuid/kick/:pilotUuid/confirm")
                .handler(ctx -> AppRBAC.teamAuthn(ctx, isOrganiserOrCaptain))
                .handler(this::confirmKickMember);
    }

    private void loadTeam(RoutingContext ctx) {
        String uuid = ctx.request().getParam("teamUuid");
        String characterName = ((JsonObject) ctx.data().get("character")).getString("characterName");
        dbClient.callDb(DbClient.DB_TEAM_BY_UUID,
                new JsonObject()
                        .put("uuid", uuid)
                        .put("characterName", characterName))
                .onFailure(ctx::fail)
                .onSuccess(result -> {
                    ctx.data().put("team", result.body());
                    ctx.next();
                });
    }

    private void manage(RoutingContext ctx) {
        render.renderPage(ctx, "/teams/manage", new JsonObject());
    }

    private void teamsData(RoutingContext ctx) {
        dbClient.callDb(DbClient.DB_TEAMS_BY_TOURNAMENT,
                new JsonObject().put("uuid", ctx.request().getParam("tournamentUuid")))
                .onFailure(ctx::fail)
                .onSuccess(result -> ctx.response().end(((JsonArray) result.body()).encode()));
    }

    private void editTeam(RoutingContext ctx) {
        render.renderPage(ctx, "/teams/edit", new JsonObject());
    }

    private void removeTeam(RoutingContext ctx) {
        render.renderPage(ctx, "/teams/remove", new JsonObject());
    }

    private void removeTeamConfirm(RoutingContext ctx) {
        dbClient.callDb(DbClient.DB_DELETE_TEAM_BY_UUID,
                ctx.request().getParam("teamUuid"))
                .onFailure(ctx::fail)
                .onSuccess(result -> doRedirect(ctx.response(), tournamentUrl(ctx, "/teams")));
    }

    private void importTeams(RoutingContext ctx) {
        render.renderPage(ctx,
                "/teams/import",
                new JsonObject()
                        .put("tsv", "")
                        .put("placeholder",
                                "One line per team - team name followed by captain, separated by a comma or tab character. e.g. \n" +
                                        "The Tuskers,Mira Chieve\n" +
                                        "Big Alliancia,Captain Jack\n" +
                                        "\n" +
                                        "Tab-separated values are the default format when copy and pasting a range from " +
                                        "Google sheets."));
    }

    private void handleImportTeams(RoutingContext ctx) {
        RequestParameters params = ctx.get("parsedParameters");
        TSV tsv = new TSV(params.formParameter("tsv").getString(), 2);

        CompositeFuture.all(tsv.stream()
                .flatMap(row -> {
                    try {
                        return Stream.of(
                                esi.lookupAlliance(webClient, row.getCol(0)),
                                esi.lookupCharacter(webClient, row.getCol(1)));
                    } catch (TSV.TSVException e) {
                        return Stream.of(Future.failedFuture(e));
                    }
                })
                .collect(Collectors.toList()))
                .map(AppStreamHelpers::toJsonObjects)
                .map(this::checkForTeamImportErrors)
                .onSuccess(msg -> {
                    if (msg.isEmpty()) {
                        dbClient.callDb(DbClient.DB_WRITE_TEAM_TSV,
                                new JsonObject()
                                        .put("tsv", tsv.json())
                                        .put("createdBy",
                                                ((JsonObject) ctx.data().get("character")).getString("characterName"))
                                        .put("uuid", ctx.request().getParam("tournamentUuid")))
                                .onFailure(t -> {
                                    String error = t.getMessage();
                                    Matcher matcher = DUPE_REGEX.matcher(error);
                                    if (matcher.find()) {
                                        error = matcher.group(2) + " is already in this tournament.";
                                    } else {
                                        t.printStackTrace();
                                    }
                                    render.renderPage(ctx,
                                            "/teams/import",
                                            new JsonObject()
                                                    .put("placeholder", "Please fix the errors and paste in the revised data.")
                                                    .put("tsv", tsv.text())
                                                    .put("errors", error));

                                })
                                .onSuccess(result -> {
                                    jobClient.run(JobClient.JOB_CHECK_ALLIANCE_MEMBERSHIP, new JsonObject());
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
                })
                .onFailure(throwable -> {
                    render.renderPage(ctx,
                            "/teams/import",
                            new JsonObject()
                                    .put("placeholder", "Please fix the errors and paste in the revised data.")
                                    .put("tsv", tsv.text())
                                    .put("errors", throwable.getMessage()));

                });
    }

    private void handleImportFail(RoutingContext ctx) {
        Throwable failure = ctx.failure();
        if (!(failure instanceof ValidationException)) {
            failure.printStackTrace();
        }
        MultiMap form = ctx.request().formAttributes();
        render.renderPage(ctx,
                "/teams/import",
                new JsonObject()
                        .put("placeholder", "Please fix the errors and paste in the revised data.")
                        .put("tsv", form.get("tsv"))
                        .put("errors", failure.getMessage()));
    }

    private void addMembers(RoutingContext ctx) {
        render.renderPage(ctx,
                "/teams/addmembers",
                new JsonObject()
                        .put("tsv", "")
                        .put("placeholder",
                                "One pilot name per line, e.g.\n\n" +
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
                dbClient.callDb(DbClient.DB_WRITE_TEAM_MEMBERS_TSV,
                        new JsonObject()
                                .put("tsv", tsv.json())
                                .put("addedBy", ((JsonObject) ctx.data().get("character")).getString("characterName"))
                                .put("uuid", ctx.request().getParam("teamUuid")))
                        .onFailure(t -> {
                            t.printStackTrace();
                            render.renderPage(ctx,
                                    "/teams/addmembers",
                                    new JsonObject()
                                            .put("placeholder", "Please fix the errors and paste in the revised data.")
                                            .put("tsv", tsv.text())
                                            .put("errors", t.getMessage()));
                        })
                        .onSuccess(results -> doRedirect(ctx.response(), teamUrl(ctx, "/edit")));
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
        if (!(failure instanceof ValidationException)) {
            failure.printStackTrace();
        }
        MultiMap form = ctx.request().formAttributes();
        render.renderPage(ctx,
                "/teams/addmembers",
                new JsonObject()
                        .put("placeholder", "Please fix the errors and paste in the revised data.")
                        .put("tsv", form.get("tsv"))
                        .put("errors", failure.getMessage()));
    }

    private void membersData(RoutingContext ctx) {
        dbClient.callDb(DbClient.DB_MEMBERS_BY_TEAM,
                ctx.request().getParam("teamUuid"))
                .onFailure(ctx::fail)
                .onSuccess(result -> ctx.response().end(((JsonArray) result.body()).encode()));
    }

    private void lockTeam(RoutingContext ctx) {
        render.renderPage(ctx, "/teams/lockteam", new JsonObject());
    }

    private void lockTeamConfirm(RoutingContext ctx) {
        dbClient.callDb(DbClient.DB_LOCK_TEAM_BY_UUID,
                ctx.request().getParam("teamUuid"))
                .onFailure(ctx::fail)
                .onSuccess(result -> doRedirect(ctx.response(), tournamentUrl(ctx, "/teams")));
    }

    private void loadPilot(RoutingContext ctx) {
        dbClient.callDb(DbClient.DB_PILOT_BY_UUID, ctx.request().getParam("pilotUuid"))
                .onFailure(ctx::fail)
                .onSuccess(result -> {
                    ctx.data().put("pilot", result.body());
                    ctx.next();
                });
    }

    private void kickMember(RoutingContext ctx) {
        render.renderPage(ctx, "/teams/confirm-kick", new JsonObject());
    }

    private void confirmKickMember(RoutingContext ctx) {
        dbClient.callDb(DbClient.DB_KICK_PILOT_BY_UUID, ctx.request().getParam("pilotUuid"))
                .onFailure(ctx::fail)
                .onSuccess(result -> {
                    doRedirect(ctx.response(), teamUrl(ctx, "/edit"));
                });
    }

    private String checkForTeamImportErrors(Stream<JsonObject> results) {
        return results.flatMap(r -> {
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
        }).collect(Collectors.joining());
    }

    public static Router routes(Vertx vertx, RenderHelper render, WebClient webClient, Esi esi, DbClient dbClient, JobClient jobClient) {
        return new TeamsRouter(vertx, render, webClient, esi, dbClient, jobClient).router();
    }

    private Router router() {
        return router;
    }

}
