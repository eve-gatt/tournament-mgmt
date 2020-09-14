package eve.toys.tournmgmt.web.routes;

import eve.toys.tournmgmt.web.Branding;
import eve.toys.tournmgmt.web.authn.AuthnRule;
import eve.toys.tournmgmt.web.authn.Role;
import eve.toys.tournmgmt.web.esi.Esi;
import eve.toys.tournmgmt.web.esi.ValidatePilotNames;
import eve.toys.tournmgmt.web.tsv.TSV;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
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

import static eve.toys.tournmgmt.web.authn.AppRBAC.tournamentAuthn;
import static toys.eve.tournmgmt.common.util.RenderHelper.doRedirect;
import static toys.eve.tournmgmt.common.util.RenderHelper.tournamentUrl;

public class TournamentRouter {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneId.of("UTC"));
    private final Router router;
    private final RenderHelper render;
    private final WebClient webClient;
    private final DbClient dbClient;
    private Esi esi;

    public TournamentRouter(Vertx vertx, RenderHelper render, WebClient webClient, Esi esi, DbClient dbClient) {
        router = Router.router(vertx);
        this.render = render;
        this.webClient = webClient;
        this.esi = esi;
        this.dbClient = dbClient;

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

        AuthnRule isOrganiser = AuthnRule.create().role(Role.ORGANISER);

        router.route("/:tournamentUuid/*").handler(this::loadTournament);
        router.get("/create").handler(this::create);
        router.post("/create")
                .handler(tournamentValidator)
                .handler(this::handleCreate)
                .failureHandler(this::handleCreateFailure);
        router.get("/:tournamentUuid/home").handler(this::home);
        router.get("/:tournamentUuid/edit")
                .handler(ctx -> tournamentAuthn(ctx, isOrganiser))
                .handler(this::edit);
        router.post("/:tournamentUuid/edit")
                .handler(ctx -> tournamentAuthn(ctx, isOrganiser))
                .handler(tournamentValidator)
                .handler(this::handleEdit)
                .failureHandler(this::handleEditFailure);
        router.get("/:tournamentUuid/roles")
                .handler(ctx -> tournamentAuthn(ctx, isOrganiser))
                .handler(this::roles);
        router.post("/:tournamentUuid/roles")
                .handler(ctx -> tournamentAuthn(ctx, isOrganiser))
                .handler(rolesValidator)
                .handler(this::handleRoles)
                .failureHandler(this::handleRolesFailure);
    }

    private void loadTournament(RoutingContext ctx) {
        String characterName = ((JsonObject) ctx.data().get("character")).getString("characterName");
        dbClient.callDb(DbClient.DB_TOURNAMENT_BY_UUID,
                new JsonObject()
                        .put("characterName", characterName)
                        .put("uuid", ctx.request().getParam("tournamentUuid")))
                .onFailure(ctx::fail)
                .onSuccess(result -> {
                    JsonObject tournament = (JsonObject) result.body();
                    ctx.data().put("tournament", tournament);
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
        dbClient.callDb(DbClient.DB_CREATE_TOURNAMENT, form)
                .onFailure(t -> {
                    form.put("practiceOnTd", form.getBoolean("practiceOnTd"));
                    form.put("playOnTd", form.getBoolean("playOnTd"));
                    ctx.put("form", form)
                            .put("errorField", "general")
                            .put("errorMessage", t.getMessage().contains("tournament_name_uindex")
                                    ? "This tournament name has already been used"
                                    : t.getMessage());
                    ctx.reroute(HttpMethod.GET, "/auth/tournament/create");
                })
                .onSuccess(result -> {
                    RenderHelper.doRedirect(ctx.response(), "/auth/profile/me");
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
        dbClient.callDb(DbClient.DB_PROBLEMS_BY_TOURNAMENT, ctx.request().getParam("tournamentUuid"))
                .onFailure(ctx::fail)
                .onSuccess(result -> {
                    JsonArray problems = (JsonArray) result.body();
                    ctx.data().put("problems", problems);
                    render.renderPage(ctx, "/tournament/home", new JsonObject());
                });

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
        dbClient.callDb(DbClient.DB_EDIT_TOURNAMENT, form)
                .onFailure(t -> {
                    form.put("practiceOnTd", form.getBoolean("practiceOnTd"));
                    form.put("playOnTd", form.getBoolean("playOnTd"));
                    ctx.put("form", form)
                            .put("errorField", "general")
                            .put("errorMessage",
                                    t.getMessage().contains("tournament_name_uindex")
                                            ? "This tournament name has already been used"
                                            : t.getMessage());
                    ctx.reroute(HttpMethod.GET, "/auth/tournament/" + ctx.request().getParam("tournamentUuid") + "/edit");
                })
                .onSuccess(result -> {
                    RenderHelper.doRedirect(ctx.response(), "/auth/profile/me");
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

        Future.<String>future(promise -> new ValidatePilotNames(webClient, esi).validate(tsv, promise))
                .onSuccess(validationMsg -> {
                    if (validationMsg.isEmpty()) {
                        dbClient.callDb(DbClient.DB_REPLACE_ROLES_BY_TYPE_AND_TOURNAMENT, new JsonObject()
                                .put("type", type.getString())
                                .put("tournamentUuid", tournamentUuid)
                                .put("names", tsv.json())
                        ).onFailure(t -> {
                            addRolesToContext(tournamentUuid,
                                    ctx.data(),
                                    ar -> render.renderPage(ctx, "/role/edit", new JsonObject().put("errors", t.getMessage())));
                        }).onSuccess(results -> {
                            ctx.user().clearCache();
                            doRedirect(ctx.response(), tournamentUrl(ctx, "/roles"));
                        });
                    } else {
                        addRolesToContext(tournamentUuid,
                                ctx.data(),
                                ar -> render.renderPage(ctx, "/role/edit", new JsonObject().put("errors", validationMsg)));
                    }
                }).onFailure(ctx::fail);
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
        dbClient.callDb(DbClient.DB_ROLES_BY_TOURNAMENT, tournamentUuid)
                .onFailure(t -> handler.handle(Future.failedFuture(t)))
                .onSuccess(result -> {
                    JsonArray roles = (JsonArray) result.body();
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
                    handler.handle(Future.succeededFuture(result));
                });
    }

    public static Router routes(Vertx vertx, RenderHelper render, WebClient webClient, Esi esi, DbClient dbClient) {
        return new TournamentRouter(vertx, render, webClient, esi, dbClient).router();
    }

    private Router router() {
        return router;
    }
}
