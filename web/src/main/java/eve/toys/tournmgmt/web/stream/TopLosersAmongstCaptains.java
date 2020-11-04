package eve.toys.tournmgmt.web.stream;

import toys.eve.tournmgmt.db.HistoricalClient;

import java.util.Map;

public class TopLosersAmongstCaptains extends TopWinnersOrLosersAmongstCaptains {

    public TopLosersAmongstCaptains(HistoricalClient historical, Map<Integer, String> tournaments) {
        super(historical, tournaments, "L");
    }

    @Override
    public String getCustom(String customProperty) {
        switch (customProperty) {
            case "grouper":
                return "Player";
            case "sublabel":
                return "losses";
            default:
                return null;
        }
    }

}
