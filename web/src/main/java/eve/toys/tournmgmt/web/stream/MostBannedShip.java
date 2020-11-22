package eve.toys.tournmgmt.web.stream;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import toys.eve.tournmgmt.db.DbClient;

public class MostBannedShip implements Command {
    private final DbClient dbClient;

    public MostBannedShip(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    @Override
    public Future<JsonObject> fetchData() {
        Promise<JsonObject> promise = Promise.promise();
        JsonObject data = new JsonObject()
                .put("Barghest", 62)
                .put("Curse", 43)
                .put("Rattlesnake", 34)
                .put("Sleipnir", 28)
                .put("Bhaalgorn", 23)
                .put("Leshak", 18)
                .put("Cerberus", 16)
                .put("Nighthawk", 14)
                .put("Scorpion Navy Issue", 12)
                .put("Blackbird", 12)
                .put("Paladin", 12)
                .put("Kitsune", 12)
                .put("Vindicator", 11)
                .put("Scimitar", 10)
                .put("Rapier", 9)
                .put("Huginn", 8)
                .put("Rook", 7)
                .put("Oneiros", 6)
                .put("Armageddon", 6)
                .put("Guardian", 5)
                .put("Falcon", 5)
                .put("Arbitrator", 4)
                .put("Loki", 4)
                .put("Widow", 4)
                .put("Pilgrim", 3)
                .put("Zarmazd", 3)
                .put("Deacon", 3)
                .put("Stork", 2)
                .put("Jackdaw", 2)
                .put("Sacrilege", 2)
                .put("Kirin", 2)
                .put("Astarte", 2)
                .put("Scalpel", 2)
                .put("Eos", 2)
                .put("Thalia", 2)
                .put("Bifrost", 2)
                .put("Caracal", 1)
                .put("Typhoon Fleet Issue", 1)
                .put("Hyena", 1)
                .put("Absolution", 1);

        promise.complete(new JsonObject().put("data", data));
        return promise.future();
    }

}
