package eve.toys.tournmgmt.web.routes;

import eve.toys.tournmgmt.web.esi.Esi;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import toys.eve.tournmgmt.common.util.RenderHelper;
import toys.eve.tournmgmt.db.DbClient;
import toys.eve.tournmgmt.db.HistoricalClient;
import toys.eve.tournmgmt.db.HistoricalClient.Call;

import java.util.*;
import java.util.stream.Collectors;

public class StreamRouter {

    private static final double SHIP_APPEARANCE_THRESHOLD = 10d;

    private final RenderHelper render;
    private final DbClient dbClient;
    private final HistoricalClient historical;
    private final Esi esi;
    private final Router router;
    private final EventBus eventBus;
    private final Map<Integer, String> tournaments = new HashMap<>();

    public StreamRouter(Vertx vertx, RenderHelper render, DbClient dbClient, HistoricalClient historical, Esi esi) {
        router = Router.router(vertx);
        this.render = render;
        this.dbClient = dbClient;
        this.historical = historical;
        this.esi = esi;
        this.eventBus = vertx.eventBus();

        initialiseTournaments();

        router.get("/stream/:code/overlay")
                .handler(this::checkCode)
                .handler(this::defaultOverlay);
        router.get("/stream/:code/overlay/:number")
                .handler(this::checkCode)
                .handler(this::overlayNumber);

        router.get("/auth/stream/manage").handler(this::manage);
        router.post("/auth/stream/manage/:number").handler(this::switchTo);

        router.get("/auth/tournament/:tournamentUuid/stream/deck").handler(this::deckHome);

        router.get("/stream/:tournamentUuid/matches/latest-match/data").handler(this::latestMatch);
        router.get("/stream/:tournamentUuid/history/:name").handler(this::historicalDataForTeam);
        router.get("/stream/sankey/data").handler(this::sankeyData);
        router.get("/stream/pickrate/data").handler(this::pickrateData);
        router.get("/stream/matchwins/data").handler(this::matchwinsData);
    }

    public static Router routes(Vertx vertx, RenderHelper render, DbClient dbClient, HistoricalClient historical, Esi esi) {
        return new StreamRouter(vertx, render, dbClient, historical, esi).router();
    }

    private void matchwinsData(RoutingContext ctx) {
        historical.callDb(Call.HISTORICAL_WINS_BY_TOURNAMENT_AND_TEAM, null)
                .onFailure(ctx::fail)
                .map(msg -> (JsonArray) msg.body())
                .onSuccess(rows -> {
                    JsonArray out = new JsonArray(rows.stream()
                            .map(o -> (JsonObject) o)
                            .map(this::withTournamentName)
                            .collect(Collectors.toList()));
                    ctx.response().end(out.encode());
                });
    }

    private void pickrateData(RoutingContext ctx) {
        historical.callDb(Call.HISTORICAL_WIN_LOSS_BY_TOURNAMENT_AND_SHIP, null)
                .onFailure(ctx::fail)
                .map(msg -> (JsonArray) msg.body())
                .onSuccess(rows -> {
                    Set<String> distinctClasses = rows.stream()
                            .map(o -> (JsonObject) o)
                            .map(r -> r.getString("SuperClass"))
                            .collect(Collectors.toSet());
                    List<String> distinctTournaments = rows.stream()
                            .map(o -> (JsonObject) o)
                            .sorted(Comparator.comparing(d -> d.getInteger("Tournament")))
                            .map(r -> tournaments.getOrDefault(r.getInteger("Tournament"), String.valueOf(r.getInteger("Tournament"))))
                            .distinct()
                            .collect(Collectors.toList());
                    Map<Tuple2<String, String>, Integer> tournamentClass = new HashMap<>();
                    rows.stream()
                            .map(o -> (JsonObject) o)
                            .forEach(row -> {
                                String tournamentName = tournaments.getOrDefault(row.getInteger("Tournament"), String.valueOf(row.getInteger("Tournament")));
                                tournamentClass.merge(Tuple.of(tournamentName, row.getString("SuperClass")),
                                        row.getInteger("used"),
                                        Integer::sum);
                            });
                    JsonArray out = new JsonArray(distinctClasses.stream()
                            .map(shipClass -> {
                                return new JsonObject()
                                        .put("id", shipClass)
                                        .put("values", new JsonArray(distinctTournaments.stream()
                                                .map(t -> new JsonObject()
                                                        .put("tournament", t)
                                                        .put("used", tournamentClass.getOrDefault(Tuple.of(t, shipClass), 0)))
                                                .collect(Collectors.toList())));
                            })
                            .collect(Collectors.toList()));
                    ctx.response().end(out.encode());
                });
    }

    private void sankeyData(RoutingContext ctx) {

        JsonObject out = new JsonObject();
        JsonArray nodes = new JsonArray();
        JsonArray links = new JsonArray();

        out.put("nodes", nodes);
        out.put("links", links);

        historical.callDb(Call.HISTORICAL_WIN_LOSS_BY_TOURNAMENT_AND_SHIP, null)
                .onFailure(ctx::fail)
                .map(msg -> (JsonArray) msg.body())
                .onSuccess(rows -> {
                    Set<Tuple2<String, String>> distinctShips = rows.stream()
                            .map(o -> (JsonObject) o)
                            .map(r -> Tuple.of(r.getString("Ship"), r.getString("Class")))
                            .collect(Collectors.toSet());
                    List<String> distinctTournaments = rows.stream()
                            .map(o -> (JsonObject) o)
                            .sorted(Comparator.comparing(d -> d.getInteger("Tournament")))
                            .map(r -> tournaments.getOrDefault(r.getInteger("Tournament"), String.valueOf(r.getInteger("Tournament"))))
                            .distinct()
                            .collect(Collectors.toList());
                    Map<Tuple2<String, String>, Integer> tournamentToShip = new HashMap<>();
                    Map<Tuple2<String, String>, Integer> shipToWinLoss = new HashMap<>();
                    rows.stream()
                            .map(o -> (JsonObject) o)
                            .forEach(row -> {
                                String tournamentName = tournaments.getOrDefault(row.getInteger("Tournament"), String.valueOf(row.getInteger("Tournament")));
                                tournamentToShip.merge(Tuple.of(tournamentName, row.getString("Ship")),
                                        row.getInteger("used"),
                                        Integer::sum);
                                shipToWinLoss.merge(
                                        Tuple.of(row.getString("Ship"), row.getString("winloss")),
                                        row.getInteger("used"),
                                        Integer::sum);
                            });


                    Map<String, IntSummaryStatistics> shipUsage = tournamentToShip.keySet().stream()
                            .collect(Collectors.groupingBy(Tuple2::_2, Collectors.summarizingInt(tournamentToShip::get)));

/*
                    Set<String> removeShips = shipUsage.keySet().stream()
                            .filter(ship -> shipUsage.get(ship).getAverage() <= SHIP_APPEARANCE_THRESHOLD)
                            .collect(Collectors.toSet());
*/
                    Set<String> removeShips = distinctShips.stream()
                            .filter(shipAndClass -> {
                                int wins = shipToWinLoss.getOrDefault(Tuple.of(shipAndClass._1(), "W"), 0);
                                int losses = shipToWinLoss.getOrDefault(Tuple.of(shipAndClass._1(), "L"), 0);
                                double winLossRatio = 1d * wins / (wins + losses);
//                                return !shipAndClass._2().contains("Logistics");
//                                return false;
//                                return losses > 0;
//                                return winLossRatio < 0.8d;
                                return false;
                            })
                            .map(shipAndClass -> shipAndClass._1())
                            .collect(Collectors.toSet());

                    distinctShips.removeAll(removeShips);

                    List<String> nodesStaging = new ArrayList<>();
                    nodesStaging.add("win");
                    nodesStaging.add("loss");
                    nodesStaging.addAll(distinctTournaments);
                    nodesStaging.addAll(distinctShips.stream().map(Tuple2::_1).collect(Collectors.toList()));

                    nodesStaging.stream()
                            .map(n -> new JsonObject().put("id", n))
                            .forEach(nodes::add);

                    tournamentToShip.keySet().stream()
                            .filter(k -> !removeShips.contains(k._2()))
                            .map(d -> new JsonObject()
                                    .put("source", nodesStaging.indexOf(d._1()))
                                    .put("target", nodesStaging.indexOf(d._2()))
                                    .put("value", tournamentToShip.get(d)))
                            .forEach(links::add);

                    shipToWinLoss.keySet().stream()
                            .filter(k -> !removeShips.contains(k._1()))
                            .map(d -> new JsonObject()
                                    .put("source", nodesStaging.indexOf(d._1()))
                                    .put("target", d._2().equals("W") ? nodesStaging.indexOf("win") : nodesStaging.indexOf("loss"))
                                    .put("value", shipToWinLoss.get(d)))
                            .forEach(links::add);
                    ctx.response().end(out.encode());
                });
    }

    private void deckHome(RoutingContext ctx) {
        JsonObject tournament = (JsonObject) ctx.data().get("tournament");
        dbClient.callDb(DbClient.DB_FETCH_STREAMER_TOKEN, tournament.getString("name"))
                .onFailure(ctx::fail)
                .onSuccess(msg -> {
                    String uuid = ((JsonObject) msg.body()).getString("uuid");
                    render.renderPage(ctx, "/stream/deck-home",
                            new JsonObject().put("streamerOverlayUrl", System.getenv("BASE_URL") + "/stream/" + uuid + "/overlay/5"));
                });
    }

    private void initialiseTournaments() {
        tournaments.put(-1, "AT9");
        tournaments.put(-2, "AT8");
        tournaments.put(-3, "AT7");
        tournaments.put(0, "AT10");
        tournaments.put(1, "AT11");
        tournaments.put(4, "AT12");
        tournaments.put(6, "AT13");
        tournaments.put(15, "AT14");
        tournaments.put(16, "AT15");
        tournaments.put(17, "AT16");
    }

    private void historicalDataForTeam(RoutingContext ctx) {
        historical.callDb(Call.HISTORICAL_FETCH_MATCHES_BY_TEAM, ctx.request().getParam("name"))
                .map(msg -> (JsonArray) msg.body())
                .map(data -> new JsonArray(data.stream()
                        .map(o -> (JsonObject) o)
                        .map(match -> withTournamentName(match))
                        .collect(Collectors.toList()))
                )
                .onFailure(ctx::fail)
                .onSuccess(data -> ctx.response().end(data.encode()));
    }

    private JsonObject withTournamentName(JsonObject match) {
        int tournamentId = match.getInteger("Tournament");
        String tournamentName = tournaments.getOrDefault(tournamentId, "unknown");
        return match.put("tournamentName", tournamentName);
    }

    private void latestMatch(RoutingContext ctx) {
        dbClient.callDb(DbClient.DB_LATEST_MATCH, null)
                .map(msg -> (JsonObject) msg.body())
                .map(RenderHelper::formatCreatedAt)
                .onFailure(ctx::fail)
                .onSuccess(match -> ctx.response().end(match.encode()));
    }

    private void overlayNumber(RoutingContext ctx) {
        int number = Integer.parseInt(ctx.request().getParam("number"));
        render.renderPage(ctx, "/stream/" + number, new JsonObject());
    }

    private void switchTo(RoutingContext ctx) {
        int number = Integer.parseInt(ctx.request().getParam("number"));
        JsonObject character = (JsonObject) ctx.data().get("character");
        String characterName = character.getString("characterName");

        dbClient.callDb(DbClient.DB_FETCH_STREAMER_TOKEN, characterName)
                .onFailure(ctx::fail)
                .onSuccess(msg -> {
                    String uuid = ((JsonObject) msg.body()).getString("uuid");
                    eventBus.publish("streamer.do-reload." + uuid,
                            new JsonObject()
                                    .put("location", "/stream/" + uuid + "/overlay/" + number));
                    ctx.response().end("{}");
                });
    }

    private void manage(RoutingContext ctx) {
        render.renderPage(ctx, "/stream/manage", new JsonObject());
    }

    private void checkCode(RoutingContext ctx) {
        String code = ctx.request().getParam("code");

        try {
            UUID.fromString(code);
        } catch (IllegalArgumentException e) {
            ctx.fail(403);
            return;
        }
        dbClient.callDb(DbClient.DB_STREAMER_BY_CODE, code)
                .onFailure(ctx::fail)
                .onSuccess(msg -> {
                    JsonArray results = (JsonArray) msg.body();
                    if (results.size() == 1) {
                        ctx.data().put("streamerCode", code);
                        ctx.data().put("streamerName", results.getJsonObject(0).getString("name"));
                        ctx.next();
                    } else {
                        ctx.fail(403);
                    }
                });
    }

    private void defaultOverlay(RoutingContext ctx) {
        render.renderPage(ctx, "/stream/1", new JsonObject());
    }

    private Router router() {
        return router;
    }

}
