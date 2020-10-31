package toys.eve.tournmgmt.db;

import io.vertx.core.Future;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;

public class HistoricalClient {

    private final EventBus eventBus;

    public HistoricalClient(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    public <T> Future<Message<Object>> callDb(Call call, T params) {
        return Future.future(promise -> eventBus.request(call.name(), params, promise));
    }

    public enum Call {
        HISTORICAL_FETCH_MATCHES_BY_TEAM,
        HISTORICAL_WIN_LOSS_BY_TOURNAMENT_AND_SHIP,
        HISTORICAL_WINS_BY_TOURNAMENT_AND_TEAM,
        HISTORICAL_WINS_BY_TOURNAMENT_AND_PLAYER;
    }
}
