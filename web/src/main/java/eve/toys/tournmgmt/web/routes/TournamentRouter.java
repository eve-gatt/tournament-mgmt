package eve.toys.tournmgmt.web.routes;


import eve.toys.tournmgmt.web.Branding;
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

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.stream.Collector;

public class TournamentRouter {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneId.of("UTC"));
    private final Router router;
    private final EventBus eventBus;
    private final RenderHelper render;

    public TournamentRouter(Vertx vertx, RenderHelper render) {
        router = Router.router(vertx);
        eventBus = vertx.eventBus();
        this.render = render;

        HTTPRequestValidationHandler tournamentValidator = HTTPRequestValidationHandler.create()
                .addFormParamWithCustomTypeValidator("name",
                        ParameterTypeValidator.createStringTypeValidator("\\p{ASCII}+", 3, 255, null),
                        true,
                        false)
                .addFormParam("startDate", ParameterType.DATE, true)
                .addFormParamWithCustomTypeValidator("practiceOnTd", new CheckboxValidator(false), false, true)
                .addFormParamWithCustomTypeValidator("playOnTd", new CheckboxValidator(false), false, true);

        router.route("/:tournamentUuid/*").handler(this::loadTournament);
        router.get("/create").handler(this::create);
        router.post("/create")
                .handler(BodyHandler.create())
                .handler(tournamentValidator)
                .handler(this::handleCreate)
                .failureHandler(this::handleCreateFailure);
        router.get("/:tournamentUuid/edit").handler(this::edit);
        router.post("/:tournamentUuid/edit")
                .handler(BodyHandler.create())
                .handler(tournamentValidator)
                .handler(this::handleEdit)
                .failureHandler(this::handleEditFailure);
        router.get("/:tournamentUuid/roles").handler(this::roles);
    }

    private void roles(RoutingContext ctx) {
        render.renderPage(ctx, "/role/edit", new JsonObject());
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
        form.put("createdBy", ((JsonObject) ctx.data().get("character")).getString("characterName"));
        form.put("parsedStartDate", LocalDate.parse(form.getString("startDate"), DATE_FORMAT).atStartOfDay(ZoneId.of("UTC")).toInstant());
        eventBus.request(DbClient.DB_CREATE_TOURNAMENT,
                form,
                msg -> {
                    if (msg.failed()) {
                        form.put("practiceOnTd", form.getBoolean("practiceOnTd"));
                        form.put("playOnTd", form.getBoolean("playOnTd"));
                        ctx.put("form", form)
                                .put("errorField", "general")
                                .put("errorMessage",
                                        msg.cause().getMessage().contains("tournament_name_uindex")
                                                ? "This tournament name has already been used"
                                                : msg.cause().getMessage());
                        ctx.reroute(HttpMethod.GET, "/auth/tournament/create");
                    } else {
                        RenderHelper.doRedirect(ctx.response(), "/auth/profile/me");
                    }
                });
    }

    private void handleCreateFailure(RoutingContext ctx) {
        Throwable failure = ctx.failure();
        if (failure instanceof ValidationException) {
            JsonObject form = ctx.request().formAttributes().entries().stream().collect(formEntriesToJson());
            // unvalidated form has strings not booleans for checkboxes so convert them
            form.put("practiceOnTd", form.getString("practiceOnTd", "off").equals("on"));
            form.put("playOnTd", form.getString("playOnTd", "off").equals("on"));
            ctx.put("form", form)
                    .put("errorField", ((ValidationException) failure).parameterName())
                    .put("errorMessage", failure.getMessage());
        } else {
            failure.printStackTrace();
        }
        ctx.reroute(HttpMethod.GET, "/auth/tournament/create");
    }

    private void edit(RoutingContext ctx) {
        JsonObject form = ctx.get("form");
        if (form == null) {
            JsonObject tournament = (JsonObject) ctx.data().get("tournament");
            form = new JsonObject()
                    .put("name", tournament.getString("name"))
                    .put("startDate", DATE_FORMAT.format(tournament.getInstant("start_date")))
                    .put("practiceOnTd", tournament.getBoolean("practice_on_td"))
                    .put("playOnTd", tournament.getBoolean("play_on_td"));
            ctx.put("errorField", "");
            ctx.put("errorMessage", "");
        }
        render.renderPage(ctx, "/tournament/edit", new JsonObject()
                .put("errorField", (String) ctx.get("errorField"))
                .put("errorMessage", (String) ctx.get("errorMessage"))
                .put("form", form));
    }

    private void handleEdit(RoutingContext ctx) {
        RequestParameters params = ctx.get("parsedParameters");
        JsonObject form = params.toJson().getJsonObject("form");
        form.put("uuid", ctx.request().getParam("tournamentUuid"));
        form.put("parsedStartDate", LocalDate.parse(form.getString("startDate"), DATE_FORMAT).atStartOfDay(ZoneId.of("UTC")).toInstant());
        form.put("editedBy", ((JsonObject) ctx.data().get("character")).getString("characterName"));
        eventBus.request(DbClient.DB_EDIT_TOURNAMENT,
                form,
                msg -> {
                    if (msg.failed()) {
                        form.put("practiceOnTd", form.getBoolean("practiceOnTd"));
                        form.put("playOnTd", form.getBoolean("playOnTd"));
                        ctx.put("form", form)
                                .put("errorField", "general")
                                .put("errorMessage",
                                        msg.cause().getMessage().contains("tournament_name_uindex")
                                                ? "This tournament name has already been used"
                                                : msg.cause().getMessage());
                        ctx.reroute(HttpMethod.GET, "/auth/tournament/" + ctx.request().getParam("tournamentUuid") + "/edit");
                    } else {
                        RenderHelper.doRedirect(ctx.response(), "/auth/profile/me");
                    }
                });
    }

    private void handleEditFailure(RoutingContext ctx) {
        Throwable failure = ctx.failure();
        if (failure instanceof ValidationException) {
            JsonObject form = ctx.request().formAttributes().entries().stream().collect(formEntriesToJson());
            // unvalidated form has strings not booleans for checkboxes so convert them
            form.put("practiceOnTd", form.getString("practiceOnTd", "off").equals("on"));
            form.put("playOnTd", form.getString("playOnTd", "off").equals("on"));
            ctx.put("form", form)
                    .put("errorField", ((ValidationException) failure).parameterName())
                    .put("errorMessage", failure.getMessage());
        } else {
            failure.printStackTrace();
        }
        ctx.reroute(HttpMethod.GET, "/auth/tournament/" + ctx.request().getParam("tournamentUuid") + "/edit");
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
