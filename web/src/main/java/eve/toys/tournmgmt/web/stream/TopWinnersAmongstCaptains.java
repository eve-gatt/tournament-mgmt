package eve.toys.tournmgmt.web.stream;

import toys.eve.tournmgmt.db.HistoricalClient;

import java.util.Map;

public class TopWinnersAmongstCaptains extends TopWinnersOrLosersAmongstCaptains {

    public TopWinnersAmongstCaptains(HistoricalClient historical, Map<Integer, String> tournaments) {
        super(historical, tournaments, "W");
    }

    @Override
    public String getCustom(String customProperty) {
        switch (customProperty) {
            case "grouper":
                return "Player";
            case "sublabel":
                return "wins";
            default:
                return null;
        }
    }

}
