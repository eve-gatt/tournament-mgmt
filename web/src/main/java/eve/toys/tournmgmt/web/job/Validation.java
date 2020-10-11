package eve.toys.tournmgmt.web.job;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;

public interface Validation {
    String getName();

    /**
     * @return array of problems to be inserted
     */
    Future<JsonArray> run();

    ProblemType getProblemType();
}
