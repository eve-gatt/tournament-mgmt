package eve.toys.tournmgmt.web.routes;

import eve.toys.tournmgmt.web.Branding;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.api.RequestParameters;
import io.vertx.ext.web.api.validation.HTTPRequestValidationHandler;
import io.vertx.ext.web.api.validation.ParameterTypeValidator;
import io.vertx.ext.web.handler.BodyHandler;
import toys.eve.tournmgmt.common.util.RenderHelper;
import toys.eve.tournmgmt.db.DbClient;

public class TeamsRouter {

    private final EventBus eventBus;
    private final RenderHelper render;
    private Router router;

    public TeamsRouter(Vertx vertx, RenderHelper render) {
        router = Router.router(vertx);
        this.render = render;
        this.eventBus = vertx.eventBus();
        router.get("/:tournamentUuid/*").handler(this::loadTournament);
        router.get("/:tournamentUuid/teams").handler(this::manage);
        router.get("/:tournamentUuid/teams/data").handler(this::teamsData);
        router.get("/:tournamentUuid/teams/import").handler(this::importTeams);
        HTTPRequestValidationHandler importValidator = HTTPRequestValidationHandler.create()
                .addFormParamWithCustomTypeValidator("tsv",
                        ParameterTypeValidator.createStringTypeValidator("\\p{ASCII}+", 3, null, null),
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
        System.out.println(params.formParameter("tsv"));
        RenderHelper.doRedirect(ctx.response(),
                "/auth/tournament/" + ctx.request().getParam("tournamentUuid") + "/teams/import");
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
