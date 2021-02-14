package eve.toys.tournmgmt.web.routes;

import eve.toys.tournmgmt.web.AppStreamHelpers;
import eve.toys.tournmgmt.web.Branding;
import eve.toys.tournmgmt.web.authn.AppRBAC;
import eve.toys.tournmgmt.web.authn.AuthnRule;
import eve.toys.tournmgmt.web.authn.Role;
import eve.toys.tournmgmt.web.esi.Esi;
import eve.toys.tournmgmt.web.esi.ValidatePilotNames;
import eve.toys.tournmgmt.web.job.JobClient;
import eve.toys.tournmgmt.web.tsv.TSV;
import io.vertx.core.*;
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
import java.util.regex.Matcher;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static eve.toys.tournmgmt.web.authn.AppRBAC.tournamentAuthn;
import static toys.eve.tournmgmt.common.util.RenderHelper.doRedirect;
import static toys.eve.tournmgmt.common.util.RenderHelper.tournamentUrl;

public class TournamentRouter {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneId.of("UTC"));
    private static final boolean TEAM_MUST_BE_AN_ALLIANCE = false;

    private final Router router;
    private final RenderHelper render;
    private final DbClient dbClient;
    private final JobClient jobClient;
    private Esi esi;

    public TournamentRouter(Vertx vertx, RenderHelper render, Esi esi, DbClient dbClient, JobClient jobClient) {
        router = Router.router(vertx);
        this.render = render;
        this.esi = esi;
        this.dbClient = dbClient;
        this.jobClient = jobClient;

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
        router.get("/:tournamentUuid/delete").handler(this::delete);
        router.get("/:tournamentUuid/edit")
                .handler(ctx -> tournamentAuthn(ctx, isOrganiser))
                .handler(this::edit);
        router.post("/:tournamentUuid/edit")
                .handler(ctx -> tournamentAuthn(ctx, isOrganiser))
                .handler(tournamentValidator)
                .handler(this::handleEdit)
                .failureHandler(this::handleEditFailure);
        router.get("/:tournamentUuid/teams/import")
                .handler(ctx -> AppRBAC.tournamentAuthn(ctx, isOrganiser))
                .handler(this::importTeams);
        router.post("/:tournamentUuid/teams/import")
                .handler(ctx -> AppRBAC.tournamentAuthn(ctx, isOrganiser))
                .handler(TSV.VALIDATOR)
                .handler(this::handleImportTeams)
                .failureHandler(this::handleImportFail);
        router.get("/:tournamentUuid/roles")
                .handler(ctx -> tournamentAuthn(ctx, isOrganiser))
                .handler(this::roles);
        router.post("/:tournamentUuid/roles")
                .handler(ctx -> tournamentAuthn(ctx, isOrganiser))
                .handler(rolesValidator)
                .handler(this::handleRoles)
                .failureHandler(this::handleRolesFailure);
    }

    public static Router routes(Vertx vertx, RenderHelper render, WebClient webClient, Esi esi, DbClient dbClient, JobClient jobClient) {
        return new TournamentRouter(vertx, render, esi, dbClient, jobClient).router();
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

    private void delete(RoutingContext ctx) {
        render.renderPage(ctx, "/wip", new JsonObject());
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
                    RenderHelper.doRedirect(ctx.response(), "/auth/tournament/" + ctx.request().getParam("tournamentUuid") + "/home");
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

    private void importTeams(RoutingContext ctx) {
        render.renderPage(ctx,
                "/teams/import",
                new JsonObject()
                        .put("tsv", "")
                        .put("placeholder",
                                "One line per team - team name followed by captain, separated by a comma or tab character. e.g. \n" +
                                "The Tuskers,Mira Chieve\n" +
                                "Big Alliancia,Captain Jack\n" +
                                "\n" +
                                "Tab-separated values are the default format when copy and pasting a range from " +
                                "Google sheets."));
    }

    private void handleImportTeams(RoutingContext ctx) {
        RequestParameters params = ctx.get("parsedParameters");
        TSV tsv = new TSV(params.formParameter("tsv").getString(), 2);

        CompositeFuture.all(tsv.stream()
                .flatMap(row -> Stream.of(
                        esi.lookupAlliance(row.getCol(0)),
                        esi.lookupCharacter(row.getCol(1))))
                .collect(Collectors.toList()))
                .map(AppStreamHelpers::compositeFutureToJsonObjects)
                .map(this::checkForTeamImportErrors)
                .onSuccess(msg -> {
                    if (msg.isEmpty()) {
                        CompositeFuture.all(tsv.stream().map(row -> Future.future(promise -> esi.lookupAlliance(row.getCol(0))
                                .onFailure(promise::fail)
                                .onSuccess(result -> {
                                    if (result.getJsonObject("result") != null) {
                                        promise.complete(
                                                result.getJsonObject("lookup").getString("name")
                                                + ","
                                                + "https://images.evetech.net/alliances/"
                                                + result.getJsonArray("result").getInteger(0)
                                                + "/logo"
                                                + ","
                                                + row.getCol(1));
                                    } else {
                                        promise.complete(
                                                row.getCol(0)
                                                + ","
                                                + "https://images.evetech.net/alliances/0/logo"
                                                + ","
                                                + row.getCol(1));
                                    }
                                }))).collect(Collectors.toList()))
                                .map(f -> f.list().stream()
                                        .map(s -> (String) s)
                                        .collect(Collectors.joining("\n")))
                                .onFailure(Throwable::printStackTrace)
                                .onSuccess(newTsv -> {
                                    TSV tsv1 = new TSV(newTsv, 3);
                                    dbClient.callDb(DbClient.DB_WRITE_TEAM_TSV,
                                            new JsonObject()
                                                    .put("tsv", tsv1.json())
                                                    .put("createdBy",
                                                            ((JsonObject) ctx.data().get("character")).getString("characterName"))
                                                    .put("uuid", ctx.request().getParam("tournamentUuid")))
                                            .onFailure(t -> {
                                                String error = t.getMessage();
                                                Matcher matcher = DbClient.DUPE_REGEX.matcher(error);
                                                if (matcher.find()) {
                                                    error = matcher.group(2) + " is already in this tournament.";
                                                } else {
                                                    t.printStackTrace();
                                                }
                                                render.renderPage(ctx,
                                                        "/teams/import",
                                                        new JsonObject()
                                                                .put("placeholder", "Please fix the errors and paste in the revised data.")
                                                                .put("tsv", tsv.text())
                                                                .put("errors", error));

                                            })
                                            .onSuccess(result -> {
                                                jobClient.run(JobClient.JOB_CHECK_CAPTAIN_ALLIANCE_MEMBERSHIP, new JsonObject());
                                                doRedirect(ctx.response(), tournamentUrl(ctx, "/teams"));
                                            });
                                });
                    } else {
                        render.renderPage(ctx,
                                "/teams/import",
                                new JsonObject()
                                        .put("placeholder", "Please fix the errors and paste in the revised data.")
                                        .put("tsv", tsv.text())
                                        .put("errors", msg));
                    }
                })
                .onFailure(throwable -> {
                    render.renderPage(ctx,
                            "/teams/import",
                            new JsonObject()
                                    .put("placeholder", "Please fix the errors and paste in the revised data.")
                                    .put("tsv", tsv.text())
                                    .put("errors", throwable.getMessage()));

                });
    }

    private void handleImportFail(RoutingContext ctx) {
        Throwable failure = ctx.failure();
        if (!(failure instanceof ValidationException)) {
            failure.printStackTrace();
        }
        MultiMap form = ctx.request().formAttributes();
        render.renderPage(ctx,
                "/teams/import",
                new JsonObject()
                        .put("placeholder", "Please fix the errors and paste in the revised data.")
                        .put("tsv", form.get("tsv"))
                        .put("errors", failure.getMessage()));
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

        Future.<String>future(promise -> new ValidatePilotNames(esi).validate(tsv, "", promise))
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

    private String checkForTeamImportErrors(Stream<JsonObject> results) {
        return results.flatMap(r -> {
            String result = "";
            if (r.containsKey("error")) {
                result += r.getString("error") + "\n";
            } else {
                if (TEAM_MUST_BE_AN_ALLIANCE) {
                    if ("alliance".equals(r.getString("category"))) {
                        if (r.getJsonArray("result") == null) {
                            result += r.getString("alliance") + " is not a valid alliance\n";
                        }
                    }
                }
                if ("character".equals(r.getString("category"))) {
                    if (r.getJsonArray("result") == null) {
                        result += r.getString("character") + " is not a valid character name\n";
                    }
                }
            }
            return result.isEmpty() ? Stream.empty() : Stream.of(result);
        }).collect(Collectors.joining());
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

    private Router router() {
        return router;
    }
}
