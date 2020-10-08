package eve.toys.tournmgmt.web.job;

import io.vertx.core.Future;

public interface Validation {
    String getName();

    Future<?> run();

    ProblemType getProblemType();
}
