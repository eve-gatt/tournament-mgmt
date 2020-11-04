package eve.toys.tournmgmt.web.stream;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import toys.eve.tournmgmt.db.HistoricalClient;

import java.util.*;
import java.util.stream.Collectors;

public class Sankey implements Command {

    private final HistoricalClient historical;
    private final Map<Integer, String> tournaments;

    public Sankey(HistoricalClient historical, Map<Integer, String> tournaments) {
        this.historical = historical;
        this.tournaments = tournaments;
    }

    @Override
    public Future<JsonObject> fetchData() {
        JsonObject out = new JsonObject();
        JsonArray nodes = new JsonArray();
        JsonArray links = new JsonArray();

        out.put("nodes", nodes);
        out.put("links", links);

        Promise<JsonObject> promise = Promise.promise();

        historical.callDb(HistoricalClient.Call.HISTORICAL_WIN_LOSS_BY_TOURNAMENT_AND_SHIP, null)
                .onFailure(promise::fail)
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
                    promise.complete(out);
                });
        return promise.future();
    }
}
