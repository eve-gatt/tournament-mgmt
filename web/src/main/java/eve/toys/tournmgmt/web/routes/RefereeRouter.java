package eve.toys.tournmgmt.web.routes;

import eve.toys.tournmgmt.web.authn.AppRBAC;
import eve.toys.tournmgmt.web.authn.AuthnRule;
import eve.toys.tournmgmt.web.authn.Role;
import eve.toys.tournmgmt.web.esi.Esi;
import eve.toys.tournmgmt.web.match.RefToolInput;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.oauth2.OAuth2Auth;
import io.vertx.ext.auth.oauth2.OAuth2ClientOptions;
import io.vertx.ext.auth.oauth2.OAuth2FlowType;
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

        AuthnRule isOrganiserOrReferee = AuthnRule.create().role(Role.ORGANISER, Role.REFEREE).isSuperuser();

        HTTPRequestValidationHandler refinputs = HTTPRequestValidationHandler.create()
                .addFormParam("red", ParameterType.GENERIC_STRING, true)
                .addFormParam("blue", ParameterType.GENERIC_STRING, true);

        router.get("/:tournamentUuid/referee")
                .handler(ctx -> AppRBAC.tournamentAuthn(ctx, isOrganiserOrReferee))
                .handler(this::home);
        router.post("/:tournamentUuid/referee")
                .handler(ctx -> AppRBAC.tournamentAuthn(ctx, isOrganiserOrReferee))
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
            ctx.put("results", new JsonObject());
        }

        render.renderPage(ctx, "/referee/home",
                new JsonObject()
                        .put("form", form));
    }

    private void success(RoutingContext ctx) {
        String tournamentUuid = ctx.request().getParam("tournamentUuid");
        RequestParameters params = ctx.get("parsedParameters");
        JsonObject form = params.toJson().getJsonObject("form");
        form.put("addedBy", ((JsonObject) ctx.data().get("character")).getString("characterName"));
        JsonObject red = new JsonObject();
        JsonObject blue = new JsonObject();
        dbClient.callDb(DbClient.DB_RECORD_REFTOOL_INPUTS, form)
                .compose(v -> {
                    RefToolInput refToolInput = new RefToolInput(dbClient, esi, oauth2);
                    return CompositeFuture.all(
                            refToolInput.process(form.getString("red"), red, tournamentUuid),
                            refToolInput.process(form.getString("blue"), blue, tournamentUuid),
                            refToolInput.validateTeamMembership(tournamentUuid, form.getString("red")),
                            refToolInput.validateTeamMembership(tournamentUuid, form.getString("blue")),
                            refToolInput.validatePilotsCanFlyShips(form.getString("red")),
                            refToolInput.validatePilotsCanFlyShips(form.getString("blue"))
                            // TODO: validate rule adhere, e.g. max 3x frigates, logi exempt but different rules
                    );
                })
                .onFailure(t -> {
                    t.printStackTrace();
                    render.renderPage(ctx, "/referee/results", new JsonObject()
                            .put("red", new JsonObject().put("error", t.getMessage()))
                            .put("blue", new JsonObject().put("error", t.getMessage())));
                })
                .onSuccess(f -> {
                    System.out.println(red.encodePrettily());
                    System.out.println(blue.encodePrettily());
                    JsonObject json = new JsonObject()
                            .put("red", red)
                            .put("blue", blue);
                    render.renderPage(ctx, "/referee/results", json);
                });
    }

    private void fail(RoutingContext ctx) {
        RenderHelper.doRedirect(ctx.response(), "/auth/tournament/" + ctx.request().getParam("tournamentUuid") + "/referee");
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
