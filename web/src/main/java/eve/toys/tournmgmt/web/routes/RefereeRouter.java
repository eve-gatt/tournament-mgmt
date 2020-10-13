package eve.toys.tournmgmt.web.routes;

import eve.toys.tournmgmt.web.esi.Esi;
import eve.toys.tournmgmt.web.match.RefToolInput;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.oauth2.OAuth2Auth;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.api.RequestParameters;
import io.vertx.ext.web.api.validation.HTTPRequestValidationHandler;
import io.vertx.ext.web.api.validation.ParameterType;
import io.vertx.ext.web.api.validation.ValidationException;
import toys.eve.tournmgmt.common.util.RenderHelper;
import toys.eve.tournmgmt.db.DbClient;

import java.util.Map;
import java.util.stream.Collector;

public class RefereeRouter {

    private final RenderHelper render;
    private final DbClient dbClient;
    private final Esi esi;
    private final OAuth2Auth oauth2;
    private final Router router;

    public RefereeRouter(Vertx vertx, RenderHelper render, DbClient dbClient, Esi esi, OAuth2Auth oauth2) {
        router = Router.router(vertx);
        this.render = render;
        this.dbClient = dbClient;
        this.esi = esi;
        this.oauth2 = oauth2;

        HTTPRequestValidationHandler refinputs = HTTPRequestValidationHandler.create()
                .addFormParam("red", ParameterType.GENERIC_STRING, true)
                .addFormParam("blue", ParameterType.GENERIC_STRING, true);

        router.get("/:tournamentUuid/referee").handler(this::home);
        router.post("/:tournamentUuid/referee")
                .handler(refinputs)
                .handler(this::success)
                .blockingHandler(this::fail);
    }

    public static Router routes(Vertx vertx, RenderHelper render, DbClient dbClient, Esi esi, OAuth2Auth oauth2) {
        return new RefereeRouter(vertx, render, dbClient, esi, oauth2).router();
    }

    private void home(RoutingContext ctx) {
        JsonObject form = ctx.get("form");
        if (form == null) {
            form = new JsonObject()
                    .put("red", "")
                    .put("blue", "");
            ctx.put("errorField", "");
            ctx.put("errorMessage", "");
            ctx.put("results", new JsonObject());
        }

        render.renderPage(ctx, "/referee/home",
                new JsonObject()
                        .put("form", form)
                        .put("errorField", (String) ctx.get("errorField"))
                        .put("errorMessage", (String) ctx.get("errorMessage")));
    }

    private void success(RoutingContext ctx) {
        String tournamentUuid = ctx.request().getParam("tournamentUuid");
        RequestParameters params = ctx.get("parsedParameters");
        JsonObject form = params.toJson().getJsonObject("form");
        form.put("addedBy", ((JsonObject) ctx.data().get("character")).getString("characterName"));
        dbClient.callDb(DbClient.DB_RECORD_REFTOOL_INPUTS, form)
                .compose(v -> {
                    RefToolInput refToolInput = new RefToolInput(dbClient, esi, oauth2);
                    return CompositeFuture.all(
                            refToolInput.validateTeamMembership(tournamentUuid, form.getString("red")),
                            refToolInput.validateTeamMembership(tournamentUuid, form.getString("blue")),
                            refToolInput.validatePilotsCanFlyShips(form.getString("red")),
                            refToolInput.validatePilotsCanFlyShips(form.getString("blue")));
                })
                .onFailure(ctx::fail)
                .onSuccess(f -> {
                    ctx.put("form", form)
                            .put("errorField", "")
                            .put("errorMessage", "")
                            .put("results", new JsonObject()
                                    .put("red", f.list().get(0).toString() + "\n" + f.list().get(2).toString())
                                    .put("blue", f.list().get(1).toString() + "\n" + f.list().get(3).toString()));
                    ctx.reroute(HttpMethod.GET, "/auth/tournament/" + tournamentUuid + "/referee");
                });
        // TODO: validate rule adhere, e.g. max 3x frigates, logi exempt but different rules
        // TODO: validate pilots are all in the same team
    }

    private void fail(RoutingContext ctx) {
        RenderHelper.doRedirect(ctx.response(), "/auth/tournament/" + ctx.request().getParam("tournamentUuid") + "/referee");
        Throwable failure = ctx.failure();
        if (failure instanceof ValidationException) {
            JsonObject form = ctx.request().formAttributes().entries().stream().collect(formEntriesToJson());
            ctx.put("form", form)
                    .put("errorField", ((ValidationException) failure).parameterName())
                    .put("errorMessage", failure.getMessage());
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
