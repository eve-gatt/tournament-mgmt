package eve.toys.tournmgmt.web.routes;


import eve.toys.tournmgmt.web.Branding;
import eve.toys.tournmgmt.web.authn.AppRBAC;
import eve.toys.tournmgmt.web.esi.Esi;
import eve.toys.tournmgmt.web.esi.ValidatePilotNames;
import eve.toys.tournmgmt.web.tsv.TSV;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.api.RequestParameter;
import io.vertx.ext.web.api.RequestParameters;
import io.vertx.ext.web.api.validation.HTTPRequestValidationHandler;
import io.vertx.ext.web.api.validation.ParameterType;
import io.vertx.ext.web.api.validation.ParameterTypeValidator;
import io.vertx.ext.web.api.validation.ValidationException;
import io.vertx.ext.web.client.WebClient;
import toys.eve.tournmgmt.common.util.RenderHelper;
import toys.eve.tournmgmt.db.DbClient;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import static eve.toys.tournmgmt.web.authn.AppRBAC.hasTournamentRole;
import static toys.eve.tournmgmt.common.util.RenderHelper.doRedirect;
import static toys.eve.tournmgmt.common.util.RenderHelper.tournamentUrl;

public class TournamentRouter {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneId.of("UTC"));
    private final Router router;
    private final EventBus eventBus;
    private final RenderHelper render;
    private final WebClient webClient;
    private Esi esi;

    public TournamentRouter(Vertx vertx, RenderHelper render, WebClient webClient, Esi esi) {
        router = Router.router(vertx);
        eventBus = vertx.eventBus();
        this.render = render;
        this.webClient = webClient;
        this.esi = esi;

        HTTPRequestValidationHandler tournamentValidator = HTTPRequestValidationHandler.create()
                .addFormParamWithCustomTypeValidator("name",
                        ParameterTypeValidator.createStringTypeValidator("\\p{ASCII}+", 3, 255, null),
                        true,
                        false)
                .addFormParam("startDate", ParameterType.DATE, true)
                .addFormParamWithCustomTypeValidator("practiceOnTd", new CheckboxValidator(false), false, true)
                .addFormParamWithCustomTypeValidator("playOnTd", new CheckboxValidator(false), false, true);

        HTTPRequestValidationHandler rolesValidator = HTTPRequestValidationHandler.create()
                .addFormParam("tsv", ParameterType.GENERIC_STRING, true)
                .addFormParam("type", ParameterType.GENERIC_STRING, true);

        router.route("/:tournamentUuid/*").handler(this::loadTournament);
        router.get("/create").handler(this::create);
        router.post("/create")
                .handler(tournamentValidator)
                .handler(this::handleCreate)
                .failureHandler(this::handleCreateFailure);
        router.get("/:tournamentUuid/home").handler(this::home);
        router.get("/:tournamentUuid/edit")
                .handler(ctx -> hasTournamentRole(ctx, "organiser"))
                .handler(this::edit);
        router.post("/:tournamentUuid/edit")
                .handler(ctx -> hasTournamentRole(ctx, "organiser"))
                .handler(tournamentValidator)
                .handler(this::handleEdit)
                .failureHandler(this::handleEditFailure);
        router.get("/:tournamentUuid/roles")
                .handler(ctx -> hasTournamentRole(ctx, "organiser"))
                .handler(this::roles);
        router.post("/:tournamentUuid/roles")
                .handler(ctx -> hasTournamentRole(ctx, "organiser"))
                .handler(rolesValidator)
                .handler(this::handleRoles)
                .failureHandler(this::handleRolesFailure);
    }

    private void loadTournament(RoutingContext ctx) {
        eventBus.request(DbClient.DB_TOURNAMENT_BY_UUID,
                ctx.request().getParam("tournamentUuid"),
                ar -> {
                    if (ar.failed()) {
                        ctx.fail(ar.cause());
                        return;
                    }
                    JsonObject tournament = (JsonObject) ar.result().body();
                    String characterName = ((JsonObject) ctx.data().get("character")).getString("characterName");
                    ctx.user().isAuthorised("organiser:" + tournament.getString("uuid"), ar2 -> {
                        if (ar2.failed()) {
                            ar2.cause().printStackTrace();
                            ctx.fail(ar2.cause());
                        } else {
                            if (ar2.result()
                                    || AppRBAC.isSuperuser(characterName)
                                    || characterName.equals(tournament.getString("created_by"))) {
                                AppRBAC.addPermissionsToTournament(tournament);
                            }
                            ctx.data().put("tournament", tournament);
                            ctx.data().put("tournament_styles", Branding.EVE_NT_STYLES);
                            ctx.next();
                        }
                    });
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

    private void home(RoutingContext ctx) {
        render.renderPage(ctx, "/tournament/home", new JsonObject());
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

    private void roles(RoutingContext ctx) {
        addRolesToContext(ctx.request().getParam("tournamentUuid"),
                ctx.data(),
                ar -> {
                    if (ar.failed()) {
                        ar.cause().printStackTrace();
                        RenderHelper.doRedirect(ctx.response(), RenderHelper.tournamentUrl(ctx, "/roles"));
                        return;
                    }
                    render.renderPage(ctx, "/role/edit", new JsonObject());
                });
    }

    private void handleRoles(RoutingContext ctx) {
        RequestParameters params = ctx.get("parsedParameters");
        TSV tsv = new TSV(params.formParameter("tsv").getString(), 1);
        RequestParameter type = params.formParameter("type");
        String tournamentUuid = ctx.request().getParam("tournamentUuid");

        Future<String> validate = Future.future(promise -> new ValidatePilotNames(webClient, esi).validate(tsv, promise));
        Future<JsonObject> writeToDB = Future.future(promise -> {
            eventBus.request(DbClient.DB_REPLACE_ROLES_BY_TYPE_AND_TOURNAMENT,
                    new JsonObject()
                            .put("type", type.getString())
                            .put("tournamentUuid", tournamentUuid)
                            .put("names", tsv.json()),
                    ar -> {
                        if (ar.failed()) {
                            promise.fail(ar.cause());
                        } else {
                            promise.complete((JsonObject) ar.result().body());
                        }
                    });
        });

        validate.compose(error -> {
            if (error.isEmpty()) {
                return writeToDB;
            } else {
                return Future.succeededFuture(new JsonObject().put("errors", error));
            }
        }).onSuccess(errors -> {
            ctx.user().clearCache();
            doRedirect(ctx.response(), tournamentUrl(ctx, "/roles"));
        }).onFailure(t -> {
            addRolesToContext(tournamentUuid,
                    ctx.data(),
                    ar -> render.renderPage(ctx, "/role/edit", new JsonObject().put("errors", t.getMessage())));
        });
    }

    private void handleRolesFailure(RoutingContext ctx) {
        Throwable failure = ctx.failure();
        addRolesToContext(ctx.request().getParam("tournamentUuid"),
                ctx.data(),
                ar -> {
                    if (ar.failed()) {
                        ar.cause().printStackTrace();
                        doRedirect(ctx.response(), tournamentUrl(ctx, "/roles"));
                        return;
                    }
                    JsonObject errors = new JsonObject().put("errors", failure.getMessage());
                    render.renderPage(ctx, "/role/edit", errors);
                });
    }

    private Collector<Map.Entry<String, String>, JsonObject, JsonObject> formEntriesToJson() {
        return Collector.of(
                JsonObject::new,
                (o, e) -> o.put(e.getKey(), e.getValue()),
                JsonObject::mergeIn);
    }

    private void addRolesToContext(String tournamentUuid, Map<String, Object> data, Handler<AsyncResult<Message<Object>>> handler) {
        eventBus.request(DbClient.DB_ROLES_BY_TOURNAMENT,
                tournamentUuid,
                ar -> {
                    if (ar.failed()) {
                        handler.handle(ar);
                        return;
                    }
                    JsonArray roles = (JsonArray) ar.result().body();
                    Function<String, String> byType = type -> roles.stream()
                            .map(o -> (JsonObject) o)
                            .filter(role -> role.getString("type").equals(type))
                            .map(role -> role.getString("name"))
                            .collect(Collectors.joining("\n"));
                    JsonObject out = new JsonObject()
                            .put("organiser", byType.apply("organiser"))
                            .put("referee", byType.apply("referee"))
                            .put("staff", byType.apply("staff"));
                    data.put("roles", out);
                    handler.handle(ar);
                });
    }

    public static Router routes(Vertx vertx, RenderHelper render, WebClient webClient, Esi esi) {
        return new TournamentRouter(vertx, render, webClient, esi).router();
    }

    private Router router() {
        return router;
    }
}
