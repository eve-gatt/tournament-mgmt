package eve.toys.tournmgmt.web;

import io.vertx.core.CompositeFuture;
import io.vertx.core.json.JsonObject;

import java.util.stream.Stream;

public class AppStreamHelpers {
    public static Stream<JsonObject> compositeFutureToJsonObjects(CompositeFuture f) {
        return f.list().stream().map(o -> (JsonObject) o);
    }
}
