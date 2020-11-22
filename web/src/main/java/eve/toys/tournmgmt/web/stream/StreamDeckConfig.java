package eve.toys.tournmgmt.web.stream;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import toys.eve.tournmgmt.db.DbClient;
import toys.eve.tournmgmt.db.HistoricalClient;

import java.util.*;
import java.util.stream.Collectors;

public class StreamDeckConfig {

    private static final List<String> LOGIS = Arrays.asList(
            "T1 Support Cruiser",
            "Logistics Frigate",
            "Precursor Support Cruiser",
            "Wildcard Logistics Cruiser",
            "Precursor Logistics Cruiser",
            "T1 Support Frigate",
            "Logistics Cruiser");
    private static final List<String> EWARS = Arrays.asList(
            "Wildcard Recon Ship",
            "Recon Ship",
            "T1 EWAR Frigate",
            "Navy EWAR Frigate",
            "T1 EWAR Cruiser",
            "Electronic Attack Ship");
    private static final List<String> BURSTS = Arrays.asList(
            "Combat Battlecruiser",
//            "Navy Battlecruiser",
            "Command Destroyer",
            "Precursor Command Destroyer",
            "Command Ship",
            "Precursor Battlecruiser");

    private final List<Widget> widgets = new ArrayList<>();

    public StreamDeckConfig(List<Widget> widgets) {
        this.widgets.addAll(widgets);
        this.widgets.forEach(w -> System.out.println("Configured: " + w.widgetName()));
    }

    public static StreamDeckConfig configure(DbClient dbClient, HistoricalClient historical) {

        Map<Integer, String> tournaments = new HashMap<>();
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

        Widget clear = new Widget(WidgetType.CLEAR, "Clear", new ClearCommand());
        Widget redTeamHistory = new Widget(WidgetType.TEAM_HISTORY, "Red Team History", new TeamHistory(dbClient, historical, tournaments, "red", "Rote Kapelle"));
        Widget blueTeamHistory = new Widget(WidgetType.TEAM_HISTORY, "Blue Team History", new TeamHistory(dbClient, historical, tournaments, "blue", "Templis CALSF"));
        Widget sankey = new Widget(WidgetType.SANKEY, "AT Ships", new Sankey(historical, tournaments));
        Widget shipChoices = new Widget(WidgetType.LINE, "Ship Choices", new ShipChoices(historical, tournaments));
        Widget commandShipChoices = new Widget(WidgetType.LINE, "Command Ships", new CommandShipChoices(historical, tournaments));
        Widget topWinnersByTeam = new Widget(WidgetType.STACKED, "Top winners by team", new TopWinnersByTeam(historical, tournaments));
        Widget topWinnersByCaptains = new Widget(WidgetType.STACKED, "Top winners amongst captains", new TopWinnersAmongstCaptains(historical, tournaments));
        Widget topLosersByCaptains = new Widget(WidgetType.STACKED, "Top losers amongst captains", new TopLosersAmongstCaptains(historical, tournaments));
        Widget topWinRatioAmongstCaptains = new Widget(WidgetType.STACKED, "Top win ratio amongst captains", new TopWinRatioAmongstCaptains(historical, tournaments));
        Widget aoMostPickedShips = new Widget(WidgetType.BAR, "Most picked ship", new MostPickedShips(dbClient, "ship"));
        Widget aoMostPickedClass = new Widget(WidgetType.BAR, "Most picked class", new MostPickedShips(dbClient, "exact_type"));
        Widget aoMostPickedLogi = new Widget(WidgetType.BAR, "Most picked logi", new MostPickedShips(dbClient, "ship", o -> LOGIS.contains(o.getString("overlay"))));
        Widget aoMostPickedEwar = new Widget(WidgetType.BAR, "Most picked EWAR", new MostPickedShips(dbClient, "ship", o -> EWARS.contains(o.getString("overlay"))));
        Widget aoMostPickedLinks = new Widget(WidgetType.BAR, "Most picked bursters", new MostPickedShips(dbClient, "ship", o -> BURSTS.contains(o.getString("overlay"))));
        Widget matchWinsByTeam = new Widget(WidgetType.LINE2, "Match Wins By Team", new MatchWinsByTeam(dbClient));
        Widget redVsBlueCurrent = new Widget(WidgetType.RED_VS_BLUE, "RvB current", new RedVsBlue(dbClient, historical, tournaments, 0));
        Widget redVsBluePrevious = new Widget(WidgetType.RED_VS_BLUE, "RvB previous", new RedVsBlue(dbClient, historical, tournaments, 1));
        Widget aoMostBannedShips = new Widget(WidgetType.BAR, "Most banned ship", new MostBannedShip(dbClient));

        return new StreamDeckConfig(Arrays.asList(
                clear,
                redVsBlueCurrent,
                redVsBluePrevious,
//                redTeamHistory,
//                blueTeamHistory,
                sankey,
                shipChoices,
                commandShipChoices,
                topWinnersByTeam,
                topWinnersByCaptains,
                topLosersByCaptains,
                topWinRatioAmongstCaptains,
                aoMostPickedShips,
                aoMostPickedClass,
                aoMostPickedLogi,
                aoMostPickedEwar,
                aoMostPickedLinks,
                aoMostBannedShips
//                matchWinsByTeam
        ));
    }

    public Future<JsonObject> fetchData(String widgetName) {
        Optional<Widget> found = widgets.stream()
                .filter(w -> w.matchesWidgetName(widgetName))
                .findFirst();
        if (found.isPresent()) {
            return found.get().fetch();
        } else {
            return Future.failedFuture("unable to find widget for: " + widgetName);
        }
    }

    public JsonArray widgetAsJson() {
        return new JsonArray(widgets.stream()
                .map(w -> {
                    JsonObject result = new JsonObject()
                            .put("label", w.getLabel())
                            .put("name", w.widgetName())
                            .put("type", w.getWidgetType().name());
                    if (w.getWidgetType() == WidgetType.STACKED) {
                        result.put("grouper", w.getCustom("grouper"))
                                .put("sublabel", w.getCustom("sublabel"));
                    }
                    if (w.getWidgetType() == WidgetType.TEAM_HISTORY) {
                        result.put("colour", w.getCustom("colour"));
                    }
                    return result;
                })
                .collect(Collectors.toList()));
    }
}
