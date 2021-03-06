package eve.toys.tournmgmt.web.routes;

import eve.toys.tournmgmt.web.authn.AppRBAC;
import eve.toys.tournmgmt.web.authn.AuthnRule;
import eve.toys.tournmgmt.web.authn.Role;
import eve.toys.tournmgmt.web.esi.Esi;
import eve.toys.tournmgmt.web.match.RefToolInput;
import io.vertx.core.CompositeFuture;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.oauth2.OAuth2Auth;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.api.RequestParameters;
import io.vertx.ext.web.api.validation.HTTPRequestValidationHandler;
import io.vertx.ext.web.api.validation.ParameterType;
import io.vertx.ext.web.api.validation.ParameterTypeValidator;
import io.vertx.ext.web.api.validation.ValidationException;
import toys.eve.tournmgmt.common.util.RenderHelper;
import toys.eve.tournmgmt.db.DbClient;

import java.util.Map;
import java.util.stream.Collector;
import java.util.stream.Collectors;

public class RefereeRouter {

    private final RenderHelper render;
    private final DbClient dbClient;
    private final Esi esi;
    private final OAuth2Auth oauth2;
    private final EventBus eventBus;
    private final Router router;

    public RefereeRouter(Vertx vertx, RenderHelper render, DbClient dbClient, Esi esi, OAuth2Auth oauth2, EventBus eventBus) {
        router = Router.router(vertx);
        this.render = render;
        this.dbClient = dbClient;
        this.esi = esi;
        this.oauth2 = oauth2;
        this.eventBus = eventBus;

        AuthnRule isOrganiserOrReferee = AuthnRule.create().role(Role.ORGANISER, Role.REFEREE).isSuperuser();

        HTTPRequestValidationHandler refinputs = HTTPRequestValidationHandler.create()
                .addFormParamWithCustomTypeValidator("red",
                        ParameterTypeValidator.createStringTypeValidator("\\p{ASCII}+", null),
                        true,
                        false)
                .addFormParamWithCustomTypeValidator("blue",
                        ParameterTypeValidator.createStringTypeValidator("\\p{ASCII}+", null),
                        true,
                        false);

        HTTPRequestValidationHandler resultInputs = HTTPRequestValidationHandler.create()
                .addFormParam("matchId", ParameterType.INT, true)
                .addFormParamWithCustomTypeValidator("winner",
                        ParameterTypeValidator.createStringTypeValidator("\\p{ASCII}+", null),
                        true,
                        false)
                .addFormParamWithCustomTypeValidator("publish", new CheckboxValidator(false), false, true);


        router.get("/:tournamentUuid/referee")
                .handler(ctx -> AppRBAC.tournamentAuthn(ctx, isOrganiserOrReferee))
                .handler(this::home);
        router.post("/:tournamentUuid/referee")
                .handler(ctx -> AppRBAC.tournamentAuthn(ctx, isOrganiserOrReferee))
                .handler(refinputs)
                .handler(this::processInputs)
                .failureHandler(this::fail);
        router.get("/:tournamentUuid/referee/record")
                .handler(RefereeRouter::redirectToRefHome);
        router.post("/:tournamentUuid/referee/record")
                .handler(ctx -> AppRBAC.tournamentAuthn(ctx, isOrganiserOrReferee))
                .handler(this::record);
        router.get("/:tournamentUuid/referee/record-result")
                .handler(RefereeRouter::redirectToRefHome);
        router.post("/:tournamentUuid/referee/record-result")
                .handler(ctx -> AppRBAC.tournamentAuthn(ctx, isOrganiserOrReferee))
                .handler(resultInputs)
                .handler(this::recordResult)
                .failureHandler(RefereeRouter::redirectToRefHome);
    }

    public static Router routes(Vertx vertx, RenderHelper render, DbClient dbClient, Esi esi, OAuth2Auth oauth2) {
        return new RefereeRouter(vertx, render, dbClient, esi, oauth2, vertx.eventBus()).router();
    }

    private static void redirectToRefHome(RoutingContext ctx) {
        RenderHelper.doRedirect(ctx.response(), "/auth/tournament/" + ctx.request().getParam("tournamentUuid") + "/referee");
    }

    private void record(RoutingContext ctx) {
        MultiMap form = ctx.request().formAttributes();
        String tournamentUuid = ctx.request().getParam("tournamentUuid");
        String characterName = ((JsonObject) ctx.data().get("character")).getString("characterName");
        int id = Integer.parseInt(form.get("id"));
        String blueJson = form.get("blueJson");
        String redJson = form.get("redJson");
        String redTeamName = new JsonObject(redJson).getJsonArray("teams").getJsonObject(0).getString("team_name");
        String blueTeamName = new JsonObject(blueJson).getJsonArray("teams").getJsonObject(0).getString("team_name");
        CompositeFuture.all(
                dbClient.callDb(DbClient.DB_TEAM_UUID_FOR_NAME, new JsonObject()
                        .put("tournamentUuid", tournamentUuid)
                        .put("teamName", blueTeamName)),
                dbClient.callDb(DbClient.DB_TEAM_UUID_FOR_NAME, new JsonObject()
                        .put("tournamentUuid", tournamentUuid)
                        .put("teamName", redTeamName)))
                .compose(f -> {
                    String blueUuid = ((Message<String>) f.resultAt(0)).body();
                    String redUuid = ((Message<String>) f.resultAt(1)).body();
                    return dbClient.callDb(DbClient.DB_CREATE_MATCH, new JsonObject()
                            .put("inputsId", id)
                            .put("createdBy", characterName)
                            .put("tournamentUuid", tournamentUuid)
                            .put("blueTeam", blueUuid)
                            .put("redTeam", redUuid)
                            .put("blueJson", blueJson)
                            .put("redJson", redJson));
                })
                .compose(matchIdsMsg -> {
                    Integer matchId = ((JsonArray) matchIdsMsg.body()).getInteger(0);
                    return dbClient.callDb(DbClient.DB_MATCH_BY_ID, matchId)
                            .map(msg -> (JsonObject) msg.body())
                            .map(RenderHelper::formatCreatedAt);
                })
                .onFailure(ctx::fail)
                .onSuccess(match -> {
                    eventBus.publish("streamer.new-match", match.encode());
                    render.renderPage(ctx, "/referee/record-success",
                            new JsonObject()
                                    .put("matchId", match.getInteger("id"))
                                    .put("red", redTeamName)
                                    .put("blue", blueTeamName));
                });

    }

    private void recordResult(RoutingContext ctx) {
        RequestParameters params = ctx.get("parsedParameters");
        JsonObject form = params.toJson().getJsonObject("form");
        dbClient.callDb(DbClient.DB_RECORD_MATCH_RESULT, form)
                .onFailure(ctx::fail)
                .onSuccess(v -> redirectToRefHome(ctx));
    }

    private void home(RoutingContext ctx) {
        dbClient.callDb(DbClient.DB_ALL_MATCHES, null)
                .map(msg -> (JsonArray) msg.body())
                .map(arr -> arr.stream()
                        .map(o -> (JsonObject) o)
                        .map(RenderHelper::formatCreatedAt)
                        .collect(Collectors.toList()))
                .map(arr -> new JsonArray(arr.subList(arr.size() - 10, arr.size())))
                .onFailure(ctx::fail)
                .onSuccess(arr -> {
                    JsonObject form = ctx.get("form");
                    if (form == null) {
                        form = new JsonObject()
                                .put("red", "")
                                .put("blue", "");
                        ctx.put("results", new JsonObject());
                    }

                    render.renderPage(ctx, "/referee/home",
                            new JsonObject()
                                    .put("matches", arr)
                                    .put("form", form));
                });
    }

    private void processInputs(RoutingContext ctx) {
        String tournamentUuid = ctx.request().getParam("tournamentUuid");
        RequestParameters params = ctx.get("parsedParameters");
        JsonObject form = params.toJson().getJsonObject("form");
        form.put("addedBy", ((JsonObject) ctx.data().get("character")).getString("characterName"));
        JsonObject red = new JsonObject();
        JsonObject blue = new JsonObject();
        dbClient.callDb(DbClient.DB_RECORD_REFTOOL_INPUTS, form)
                .compose(msg -> {
                    RefToolInput refToolInput = new RefToolInput(dbClient, esi, oauth2);
                    return CompositeFuture.all(
                            refToolInput.process(form.getString("red"), red, tournamentUuid),
                            refToolInput.process(form.getString("blue"), blue, tournamentUuid)
                            // TODO: validate rule adhere, e.g. max 3x frigates, logi exempt but different rules
                    ).onSuccess(f -> {
                        JsonObject json = new JsonObject()
                                .put("id", msg.body())
                                .put("red", red)
                                .put("blue", blue);
                        render.renderPage(ctx, "/referee/results", json);
                    });
                })
                .onFailure(t -> {
                    t.printStackTrace();
                    render.renderPage(ctx, "/referee/results", new JsonObject()
                            .put("red", new JsonObject().put("error", t.getMessage()))
                            .put("blue", new JsonObject().put("error", t.getMessage())));
                });
    }

    private void fail(RoutingContext ctx) {
        Throwable failure = ctx.failure();
        if (failure instanceof ValidationException) {
            JsonObject form = ctx.request().formAttributes().entries().stream().collect(formEntriesToJson());
            ctx.put("form", form);
        } else {
            failure.printStackTrace();
        }
        ctx.reroute(HttpMethod.GET, "/auth/tournament/" + ctx.request().getParam("tournamentUuid") + "/referee");
    }

    private Collector<Map.Entry<String, String>, JsonObject, JsonObject> formEntriesToJson() {
        return Collector.of(
                JsonObject::new,
                (o, e) -> o.put(e.getKey(), e.getValue()),
                JsonObject::mergeIn);
    }

    private Router router() {
        return router;
    }

}
