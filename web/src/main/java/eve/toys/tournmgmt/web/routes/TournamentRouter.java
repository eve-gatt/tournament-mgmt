package eve.toys.tournmgmt.web.routes;


import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.api.RequestParameters;
import io.vertx.ext.web.api.validation.HTTPRequestValidationHandler;
import io.vertx.ext.web.api.validation.ParameterType;
import io.vertx.ext.web.api.validation.ParameterTypeValidator;
import io.vertx.ext.web.api.validation.ValidationException;
import io.vertx.ext.web.handler.BodyHandler;
import toys.eve.tournmgmt.common.util.RenderHelper;
import toys.eve.tournmgmt.db.DbClient;

import java.util.Map;
import java.util.stream.Collector;

public class TournamentRouter {

    private final Router router;
    private final EventBus eventBus;
    private final RenderHelper render;

    public TournamentRouter(Vertx vertx, RenderHelper render) {
        router = Router.router(vertx);
        eventBus = vertx.eventBus();
        this.render = render;

        HTTPRequestValidationHandler createValidator = HTTPRequestValidationHandler.create()
                .addFormParamWithCustomTypeValidator("name",
                        ParameterTypeValidator.createStringTypeValidator("\\p{ASCII}+", 3, 255, null),
                        true,
                        false)
                .addFormParam("startDate", ParameterType.DATE, true)
                .addFormParamWithCustomTypeValidator("practiceOnTd", new CheckboxValidator(false), false, true)
                .addFormParamWithCustomTypeValidator("playOnTd", new CheckboxValidator(false), false, true);

        router.get("/create").handler(this::create);
        router.post("/create")
                .handler(BodyHandler.create())
                .handler(createValidator)
                .handler(this::handleCreate)
                .failureHandler(this::handleFailure);
    }

    private void create(RoutingContext ctx) {
        JsonObject form = ctx.get("form");
        if (form == null) {
            form = new JsonObject();
            ctx.put("errorField", "");
            ctx.put("errorMessage", "");
        }
        render.renderPage(ctx, "/tournament/create", new JsonObject()
                .put("errorField", (String) ctx.get("errorField"))
                .put("errorMessage", (String) ctx.get("errorMessage"))
                .put("form", form));
    }

    private void handleCreate(RoutingContext ctx) {
        RequestParameters params = ctx.get("parsedParameters");
        JsonObject form = params.toJson().getJsonObject("form");
        eventBus.request(DbClient.DB_CREATE_TOURNAMENT,
                form,
                msg -> {
                    if (msg.failed()) {
                        form.put("practiceOnTd", form.getBoolean("practiceOnTd") ? "on" : "off");
                        form.put("playOnTd", form.getBoolean("playOnTd") ? "on" : "off");
                        ctx.put("form", form)
                                .put("errorField", "general")
                                .put("errorMessage",
                                        msg.cause().getMessage().contains("tournament_name_uindex")
                                                ? "This tournament name has already been used"
                                                : msg.cause().getMessage());
                    }
                    ctx.reroute(HttpMethod.GET, "/tournament/create");
                });
    }

    private void handleFailure(RoutingContext ctx) {
        Throwable failure = ctx.failure();
        if (failure instanceof ValidationException) {
            JsonObject form = ctx.request().formAttributes().entries().stream().collect(formEntriesToJson());
            ctx.put("form", form)
                    .put("errorField", ((ValidationException) failure).parameterName())
                    .put("errorMessage", failure.getMessage());
        } else {
            failure.printStackTrace();
        }
        ctx.reroute(HttpMethod.GET, "/tournament/create");
    }

    private Collector<Map.Entry<String, String>, JsonObject, JsonObject> formEntriesToJson() {
        return Collector.of(
                JsonObject::new,
                (o, e) -> o.put(e.getKey(), e.getValue()),
                JsonObject::mergeIn);
    }

    public static Router routes(Vertx vertx, RenderHelper render) {
        return new TournamentRouter(vertx, render).router();
    }

    private Router router() {
        return router;
    }
}
