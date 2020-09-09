package eve.toys.tournmgmt.web.esi;

import eve.toys.tournmgmt.web.tsv.TSV;
import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ValidatePilotNames {

    private final WebClient webClient;
    private final Esi esi;

    public ValidatePilotNames(WebClient webClient, Esi esi) {
        this.webClient = webClient;
        this.esi = esi;
    }

    public void validate(TSV tsv, Handler<AsyncResult<String>> replyHandler) {
        List<Future> searches = tsv.stream()
                .map(row -> esi.lookupCharacter(webClient, row.getCol(0)))
                .collect(Collectors.toList());
        CompositeFuture.all(searches)
                .onSuccess(f -> {
                    String msg = f.list().stream()
                            .map(o -> (JsonObject) o)
                            .flatMap(r -> {
                                String result = "";
                                if (r.getJsonArray("result") == null) {
                                    result += r.getString("character") + " is not a valid character name";
                                }
                                return result.isEmpty() ? Stream.empty() : Stream.of(result);
                            })
                            .collect(Collectors.joining("\n"))
                            .trim();
                    replyHandler.handle(Future.succeededFuture(msg));
                })
                .onFailure(t -> replyHandler.handle(Future.failedFuture(t)));
    }
}
