package eve.toys.tournmgmt.web.stream;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import toys.eve.tournmgmt.db.HistoricalClient;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class Config {

    private final Path ROOT_DIR = Paths.get("stream");
    private Set<Widget> widgets = new HashSet<>();

    public Config(List<Widget> widgets) {
        this.widgets.addAll(widgets);
        this.widgets.forEach(w -> System.out.println("Configured: " + w.widgetName()));
    }

    public static Config configure(HistoricalClient historical) {

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

        Widget clear = new Widget("Clear", new ClearCommand());
        Widget redTeamHistory = new Widget("Red Team History", new TeamHistory(Command.RED));
        Widget blueTeamHistory = new Widget("Blue Team History", new TeamHistory(Command.BLUE));
        Widget sankey = new Widget("Sankey", new Sankey(historical, tournaments));
        Widget shipChoices = new Widget("Ship Choices", new ShipChoices(historical, tournaments));
        Widget topWinnersByTeam = new Widget("Top winners by team", new TopWinnersByTeam(historical, tournaments));
        Widget topWinnersByCaptains = new Widget("Top winners amongst captains", new TopWinnersAmongstCaptains(historical, tournaments));
        Widget topLosersByCaptains = new Widget("Top losers amongst captains", new TopLosersAmongstCaptains(historical, tournaments));
        Widget topWinRatioAmongstCaptains = new Widget("Top win ratio amongst captains", new TopWinRatioAmongstCaptains(historical, tournaments));

        return new Config(Arrays.asList(
                clear,
                redTeamHistory,
                blueTeamHistory,
                sankey,
                shipChoices,
                topWinnersByTeam,
                topWinnersByCaptains,
                topLosersByCaptains,
                topWinRatioAmongstCaptains));
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
}
