package eve.toys.tournmgmt.web.routes;

import eve.toys.tournmgmt.web.Branding;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
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

import java.net.URLEncoder;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TeamsRouter {

    public static final String ESI_BASE = "https://esi.evetech.net/latest";
    private final EventBus eventBus;
    private final RenderHelper render;
    private Router router;

    public TeamsRouter(Vertx vertx, RenderHelper render) {
        router = Router.router(vertx);
        this.render = render;
        this.eventBus = vertx.eventBus();
        router.route("/:tournamentUuid/*").handler(this::loadTournament);
        router.get("/:tournamentUuid/teams").handler(this::manage);
        router.get("/:tournamentUuid/teams/data").handler(this::teamsData);
        router.get("/:tournamentUuid/teams/import").handler(this::importTeams);
        HTTPRequestValidationHandler importValidator = HTTPRequestValidationHandler.create()
                .addFormParamWithCustomTypeValidator("tsv",
                        ParameterTypeValidator.createStringTypeValidator("\\p{ASCII}+", 7, null, null),
                        true,
                        false);

        router.post("/:tournamentUuid/teams/import")
                .handler(BodyHandler.create())
                .handler(importValidator)
                .handler(this::handleImport)
                .failureHandler(this::handleImportFail);
    }

    public static Router routes(Vertx vertx, RenderHelper render) {
        return new TeamsRouter(vertx, render).router();
    }

    private void handleImportFail(RoutingContext ctx) {
        RenderHelper.doRedirect(ctx.response(),
                "/auth/tournament/" + ctx.request().getParam("tournamentUuid") + "/teams/import");
    }

    private void handleImport(RoutingContext ctx) {
        RequestParameters params = ctx.get("parsedParameters");
        String tsv = params.formParameter("tsv").getString();
        AccessToken token = (AccessToken) ctx.user();

        String[] rows = tsv.split("[\\r\\n]+");
        List<Future> searches = Arrays.stream(rows)
                .flatMap(row -> {
                    String[] cols = row.split("\t");
                    return Stream.of(
                            checkMembership(
                                    token,
                                    checkAlliance(token, cols[0]),
                                    checkCharacter(token, cols[1])));
                })
                .collect(Collectors.toList());

        CompositeFuture.all(searches)
                .onSuccess(f -> {
                    String msg = f.list().stream()
                            .map(o -> (JsonObject) o)
                            .flatMap(r -> {
                                String result = "";
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
                                return result.isEmpty() ? Stream.empty() : Stream.of(result);
                            })
                            .collect(Collectors.joining());
                    render.renderPage(ctx,
                            "/teams/import",
                            new JsonObject().put("placeholder", msg));
                })
                .onFailure(throwable -> {
                    throwable.printStackTrace();
                    RenderHelper.doRedirect(ctx.response(),
                            "/auth/tournament/" + ctx.request().getParam("tournamentUuid") + "/teams/import");
                });
    }

    private Future<JsonObject> checkMembership(AccessToken token,
                                               Future<JsonObject> checkAlliance,
                                               Future<JsonObject> checkCharacter) {
        return Future.future(promise ->
                CompositeFuture.all(checkAlliance, checkCharacter)
                        .onSuccess(f -> {
                            JsonObject alliance = f.resultAt(0);
                            JsonObject character = f.resultAt(1);
                            JsonObject out = new JsonObject();
                            out.put("alliance", alliance)
                                    .put("character", character);
                            if (alliance.getJsonArray("result") != null
                                    && character.getJsonArray("result") != null) {
                                token.fetch(ESI_BASE + "/characters/" + character.getJsonArray("result").getInteger(0),
                                        ar -> {
                                            if (ar.failed()) {
                                                promise.fail(ar.cause());
                                                return;
                                            }
                                            Integer allianceId = ar.result().body().toJsonObject()
                                                    .getInteger("alliance_id");
                                            out.put("membership", allianceId);
                                            promise.complete(out);
                                        });
                            } else {
                                promise.complete(out);
                            }
                        })
                        .onFailure(Throwable::printStackTrace)
        );
    }

    private Future<JsonObject> checkAlliance(AccessToken token, String alliance) {
        return Future.future(promise ->
                token.fetch(ESI_BASE + "/search/?categories=alliance&strict=true&search="
                                + URLEncoder.encode(alliance),
                        ar -> {
                            if (ar.failed()) {
                                promise.fail(ar.cause());
                                return;
                            }
                            promise.complete(new JsonObject()
                                    .put("name", alliance)
                                    .put("result", ar.result().body().toJsonObject().getJsonArray("alliance")));
                        }));
    }

    private Future<JsonObject> checkCharacter(AccessToken token, String character) {
        return Future.future(promise ->
                token.fetch(ESI_BASE + "/search/?categories=character&strict=true&search="
                                + URLEncoder.encode(character),
                        ar -> {
                            if (ar.failed()) {
                                promise.fail(ar.cause());
                                return;
                            }
                            promise.complete(new JsonObject()
                                    .put("name", character)
                                    .put("result", ar.result().body().toJsonObject().getJsonArray("character")));
                        }));
    }

    private void teamsData(RoutingContext ctx) {
        ctx.response().end("[]");
    }

    private void loadTournament(RoutingContext ctx) {
        eventBus.request(DbClient.DB_TOURNAMENT_BY_UUID,
                ctx.request().getParam("tournamentUuid"),
                ar -> {
                    if (ar.failed()) {
                        ctx.fail(ar.cause());
                        return;
                    }
                    ctx.data().put("tournament", ar.result().body());
                    ctx.data().put("tournament_styles", Branding.EVE_NT_STYLES);

                    ctx.next();
                });
    }

    private void manage(RoutingContext ctx) {
        render.renderPage(ctx, "/teams/manage", new JsonObject());
    }

    private void importTeams(RoutingContext ctx) {
        render.renderPage(ctx,
                "/teams/import",
                new JsonObject().put("placeholder",
                        "Paste values straight from spreadsheet as two column, name and captain, e.g. \n" +
                                "The Tuskers\tMira Chieve\n" +
                                "Big Alliancia\tCaptain Jack\n" +
                                "\n" +
                                "This is the default format when copy and pasting a range from a spreadsheet."));
    }

    private Router router() {
        return router;
    }
}
