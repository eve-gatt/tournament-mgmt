package eve.toys.tournmgmt.web.routes;

import eve.toys.tournmgmt.web.authn.AppRBAC;
import eve.toys.tournmgmt.web.authn.AuthnRule;
import eve.toys.tournmgmt.web.authn.Role;
import eve.toys.tournmgmt.web.esi.Esi;
import eve.toys.tournmgmt.web.esi.ValidatePilotNames;
import eve.toys.tournmgmt.web.job.JobClient;
import eve.toys.tournmgmt.web.tsv.TSV;
import eve.toys.tournmgmt.web.tsv.TSVException;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.api.RequestParameters;
import io.vertx.ext.web.api.validation.ValidationException;
import toys.eve.tournmgmt.common.util.RenderHelper;
import toys.eve.tournmgmt.db.DbClient;

import java.util.regex.Matcher;

import static toys.eve.tournmgmt.common.util.RenderHelper.*;

public class TeamsRouter {

    private final RenderHelper render;
    private final Router router;
    private final Esi esi;
    private final DbClient dbClient;
    private final JobClient jobClient;

    public TeamsRouter(Vertx vertx, RenderHelper render, Esi esi, DbClient dbClient, JobClient jobClient) {
        router = Router.router(vertx);
        this.render = render;
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
        router.get("/:tournamentUuid/teams/:teamUuid/unlock-team")
                .handler(ctx -> AppRBAC.teamAuthn(ctx, isOrganiserOrCaptain))
                .handler(ctx -> {
                    if (!ctx.failed()) {
                        boolean isLocked = ((JsonObject) ctx.data().get("team")).getBoolean("locked");
                        if (isLocked) {
                            ctx.next();
                        } else {
                            ctx.fail(403);
                        }
                    }
                })
                .handler(this::unlockTeam);
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
                .onSuccess(result -> {
                    jobClient.run(JobClient.JOB_CHECK_PILOTS_ON_ONE_TEAM, new JsonObject());
                    doRedirect(ctx.response(), tournamentUrl(ctx, "/teams"));
                });
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
        TSV tsv = new TSV(params.formParameter("tsv").getString(), 1)
                .processor(this::pilotNameProcessor);

        new ValidatePilotNames(esi).validate(tsv, ar -> {
            if (ar.failed()) {
                ar.cause().printStackTrace();
                doRedirect(ctx.response(), teamUrl(ctx, "/add-members"));
                return;
            }
            if (ar.result().isEmpty()) {
                // TODO: check if too many members after import
                // TODO: check members aren't in other teams
                tsv.validateAndProcess().onFailure(t -> {
                    t.printStackTrace();
                    doRedirect(ctx.response(), teamUrl(ctx, "/add-members"));
                }).onSuccess(v -> dbClient.callDb(DbClient.DB_WRITE_TEAM_MEMBERS_TSV,
                        new JsonObject()
                                .put("tsv", tsv.json())
                                .put("addedBy", ((JsonObject) ctx.data().get("character")).getString("characterName"))
                                .put("uuid", ctx.request().getParam("teamUuid")))
                        .onFailure(t -> {
                            String error = t.getMessage();
                            Matcher matcher = DbClient.DUPE_REGEX.matcher(error);
                            if (matcher.find()) {
                                error = matcher.group(2) + " is already in this team.";
                            } else {
                                t.printStackTrace();
                            }
                            render.renderPage(ctx,
                                    "/teams/addmembers",
                                    new JsonObject()
                                            .put("placeholder", "Please fix the errors and paste in the revised data.")
                                            .put("tsv", tsv.text())
                                            .put("errors", error));
                        })
                        .onSuccess(results -> {
                            jobClient.run(JobClient.JOB_CHECK_PILOTS_ON_ONE_TEAM, new JsonObject());
                            doRedirect(ctx.response(), teamUrl(ctx, "/edit"));
                        }));
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

    private void unlockTeam(RoutingContext ctx) {
        dbClient.callDb(DbClient.DB_TOGGLE_LOCK_TEAM_BY_UUID,
                ctx.request().getParam("teamUuid"))
                .onFailure(ctx::fail)
                .onSuccess(result -> doRedirect(ctx.response(), teamUrl(ctx, "/edit")));
    }

    private void lockTeamConfirm(RoutingContext ctx) {
        dbClient.callDb(DbClient.DB_TOGGLE_LOCK_TEAM_BY_UUID,
                ctx.request().getParam("teamUuid"))
                .onFailure(ctx::fail)
                .onSuccess(result -> doRedirect(ctx.response(), teamUrl(ctx, "/edit")));
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
                    jobClient.run(JobClient.JOB_CHECK_PILOTS_ON_ONE_TEAM, new JsonObject());
                    doRedirect(ctx.response(), teamUrl(ctx, "/edit"));
                });
    }

    private Future<String> pilotNameProcessor(TSV.Row row) {
        return Future.future(promise -> {
            try {
                esi.lookupCharacter(row.getCol(0))
                        .onFailure(promise::fail)
                        .onSuccess(json -> {
                            int characterId = json.getJsonArray("result").getInteger(0);
                            esi.fetchCharacter(characterId)
                                    .onFailure(promise::fail)
                                    .onSuccess(json2 -> {
                                        String name = json2.containsKey("error") ? json2.getString("error") : json2.getString("name");
                                        System.out.println(json2.encodePrettily());
                                        promise.complete(name);
                                    });
                        });
            } catch (TSVException e) {
                promise.fail(e);
            }
        });
    }

    public static Router routes(Vertx vertx, RenderHelper render, Esi esi, DbClient dbClient, JobClient jobClient) {
        return new TeamsRouter(vertx, render, esi, dbClient, jobClient).router();
    }

    private Router router() {
        return router;
    }

}
