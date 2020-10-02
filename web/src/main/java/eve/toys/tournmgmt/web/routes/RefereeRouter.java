package eve.toys.tournmgmt.web.routes;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
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
    private final Router router;

    public RefereeRouter(Vertx vertx, RenderHelper render, DbClient dbClient) {
        router = Router.router(vertx);
        this.render = render;
        this.dbClient = dbClient;

        HTTPRequestValidationHandler refinputs = HTTPRequestValidationHandler.create()
                .addFormParam("red", ParameterType.GENERIC_STRING, true)
                .addFormParam("blue", ParameterType.GENERIC_STRING, true);

        router.get("/:tournamentUuid/referee").handler(this::home);
        router.post("/:tournamentUuid/referee")
                .handler(refinputs)
                .handler(this::success)
                .blockingHandler(this::fail);
    }

    private void home(RoutingContext ctx) {
        JsonObject form = ctx.get("form");
        if (form == null) {
            form = new JsonObject()
                    .put("red", "")
                    .put("blue", "");
            ctx.put("errorField", "");
            ctx.put("errorMessage", "");
        }

        render.renderPage(ctx, "/referee/home",
                new JsonObject()
                        .put("form", form)
                        .put("errorField", (String) ctx.get("errorField"))
                        .put("errorMessage", (String) ctx.get("errorMessage")));
    }

    private void success(RoutingContext ctx) {
        RequestParameters params = ctx.get("parsedParameters");
        JsonObject form = params.toJson().getJsonObject("form");
        form.put("addedBy", ((JsonObject) ctx.data().get("character")).getString("characterName"));
        dbClient.callDb(DbClient.DB_RECORD_REFTOOL_INPUTS, form)
                .onFailure(ctx::fail)
                .onSuccess(msg -> {
                    RenderHelper.doRedirect(ctx.response(), "/auth/tournament/" + ctx.request().getParam("tournamentUuid") + "/referee");
                });

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

    public static Router routes(Vertx vertx, RenderHelper render, DbClient dbClient) {
        return new RefereeRouter(vertx, render, dbClient).router();
    }

    private Router router() {
        return router;
    }
}
