package eve.toys.tournmgmt.web.job;

import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;

public class JobClient {
    public static final String JOB_CHECK_CAPTAIN_ALLIANCE_MEMBERSHIP = "JOB_CHECK_ALLIANCE_MEMBERSHIP";
    public static final String JOB_CHECK_PILOTS_ON_ONE_TEAM = "JOB_CHECK_PILOTS_ON_ONE_TEAM";
    public static final String JOB_CHECK_PILOTS_ALLIANCE_MEMBERSHIP = "JOB_CHECK_PILOTS_ALLIANCE_MEMBERSHIP";

    private final EventBus eventBus;

    public JobClient(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    public void run(String jobName, JsonObject data) {
        eventBus.send(jobName, data);
    }
}
