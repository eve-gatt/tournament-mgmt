package eve.toys.tournmgmt.web.routes;

import eve.toys.tournmgmt.web.esi.Esi;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.impl.StringEscapeUtils;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.oauth2.AccessToken;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.api.RequestParameters;
import io.vertx.ext.web.api.validation.HTTPRequestValidationHandler;
import io.vertx.ext.web.api.validation.ParameterTypeValidator;
import io.vertx.ext.web.handler.BodyHandler;
import toys.eve.tournmgmt.common.util.RenderHelper;
import toys.eve.tournmgmt.db.DbClient;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TeamsRouter {

    private final EventBus eventBus;
    private final RenderHelper render;
    private Router router;

    public TeamsRouter(Vertx vertx, RenderHelper render) {
        router = Router.router(vertx);
        this.render = render;
        this.eventBus = vertx.eventBus();

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
                .handler(BodyHandler.create())
                .handler(tsvValidator)
                .handler(this::handleImport)
                .failureHandler(this::handleImportFail);
        router.get("/:tournamentUuid/teams/:teamUuid/add-members").handler(this::addMembers);
        router.post("/:tournamentUuid/teams/:teamUuid/add-members")
                .handler(BodyHandler.create())
                .handler(tsvValidator)
                .handler(this::handleAddMembers)
                .failureHandler(this::handleAddMembersFail);
        router.get("/:tournamentUuid/teams/:teamUuid/members/data").handler(this::membersData);
    }

    public static Router routes(Vertx vertx, RenderHelper render) {
        return new TeamsRouter(vertx, render).router();
    }

    private Router router() {
        return router;
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

    private void handleImport(RoutingContext ctx) {
        RequestParameters params = ctx.get("parsedParameters");
        String tsv = params.formParameter("tsv").getString();
        AccessToken token = (AccessToken) ctx.user();

        String[] rows = tsv.split("[\\r\\n]+");
        List<Future> searches = Arrays.stream(rows)
                .filter(row -> !row.trim().isEmpty())
                .flatMap(row -> {
                    String[] cols = row.split("[\\t,]");
                    if (cols.length != 2) {
                        return Stream.of(Future.succeededFuture(new JsonObject().put("error", "Didn't find 2 columns separated by a tab or comma: " + row)));
                    }
                    try {
                        return Stream.of(Esi.checkMembership(token,
                                Esi.lookupALliance(token, StringEscapeUtils.escapeJavaScript(cols[0])),
                                Esi.checkCharacter(token, StringEscapeUtils.escapeJavaScript(cols[1]))));
                    } catch (Exception e) {
                        e.printStackTrace();
                        return Stream.of(Future.succeededFuture(new JsonObject().put("error", "Error row: " + row)));
                    }
                })
                .collect(Collectors.toList());

        CompositeFuture.all(searches).onSuccess(f -> {
            String msg = f.list().stream()
                    .map(o -> (JsonObject) o)
                    .flatMap(r -> {
                        String result = "";
                        if (r.containsKey("error")) {
                            result += r.getString("error") + "\n";
                        } else {
                            if (r.getJsonObject("alliance").getJsonArray("result") == null) {
                                result += r.getJsonObject("alliance").getString("name") + " is not a valid alliance\n";
                            }
                            if (r.getJsonObject("character").getJsonArray("result") == null) {
                                result += r.getJsonObject("character").getString("name") + " is not a valid character name\n";
                            }
                            if (r.containsKey("membership")
                                    && r.getInteger("membership") == null) {
                                result += r.getJsonObject("character").getString("name") + " is not in an alliance\n";
                            }
                            if (r.containsKey("membership")
                                    && r.getInteger("membership") != null
                                    && !r.getInteger("membership")
                                    .equals(r.getJsonObject("alliance").getJsonArray("result").getInteger(0))) {
                                result += r.getJsonObject("character").getString("name") + " is not in "
                                        + r.getJsonObject("alliance").getString("name") + "\n";
                            }
                        }
                        return result.isEmpty() ? Stream.empty() : Stream.of(result);
                    })
                    .collect(Collectors.joining());

            if (msg.isEmpty()) {
                eventBus.request(DbClient.DB_WRITE_TEAM_TSV,
                        new JsonObject()
                                .put("tsv", tsv)
                                .put("createdBy",
                                        ((JsonObject) ctx.data().get("character")).getString("characterName"))
                                .put("uuid", ctx.request().getParam("tournamentUuid")),
                        ar -> {
                            if (ar.failed()) {
                                ar.cause().printStackTrace();
                                RenderHelper.doRedirect(ctx.response(), tournamentUrl(ctx, "/teams/import"));
                                return;
                            }
                            RenderHelper.doRedirect(ctx.response(), tournamentUrl(ctx, "/teams"));
                        });
            } else {
                render.renderPage(ctx,
                        "/teams/import",
                        new JsonObject()
                                .put("placeholder", "Please fix the errors and paste in the revised data.")
                                .put("tsv", tsv)
                                .put("errors", msg));
            }
        }).onFailure(throwable -> {
            throwable.printStackTrace();
            RenderHelper.doRedirect(ctx.response(), tournamentUrl(ctx, "/teams/import"));
        });
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

    private void handleAddMembers(RoutingContext ctx) {
        RequestParameters params = ctx.get("parsedParameters");
        String tsv = params.formParameter("tsv").getString();
        AccessToken token = (AccessToken) ctx.user();

        String[] rows = tsv.split("[\\r\\n]+");
        List<Future> searches = Arrays.stream(rows)
                .filter(row -> !row.trim().isEmpty())
                .map(row -> Esi.checkCharacter(token, row))
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
                eventBus.request(DbClient.DB_WRITE_TEAM_MEMBERS_TSV,
                        new JsonObject()
                                .put("tsv", tsv)
                                .put("addedBy",
                                        ((JsonObject) ctx.data().get("character")).getString("characterName"))
                                .put("uuid", ctx.request().getParam("teamUuid")),
                        ar -> {
                            if (ar.failed()) {
                                ar.cause().printStackTrace();
                                RenderHelper.doRedirect(ctx.response(), teamUrl(ctx, "/add-members"));
                                return;
                            }
                            RenderHelper.doRedirect(ctx.response(), teamUrl(ctx, "/edit"));
                        });
            } else {
                render.renderPage(ctx,
                        "/teams/addmembers",
                        new JsonObject()
                                .put("placeholder", "Please fix the errors and paste in the revised data.")
                                .put("tsv", tsv)
                                .put("errors", msg));

            }
        }).onFailure(throwable -> {
            throwable.printStackTrace();
            RenderHelper.doRedirect(ctx.response(), teamUrl(ctx, "/add-members"));
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
