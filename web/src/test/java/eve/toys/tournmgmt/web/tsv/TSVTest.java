package eve.toys.tournmgmt.web.tsv;

import io.vertx.core.Future;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class TSVTest {

    @Test
    public void basicSuccess(TestContext context) {
        TSV tsv = new TSV("gatt2111", 1)
                .validator(t -> Future.succeededFuture())
                .processor(t -> Future.succeededFuture());
        tsv.validateAndProcess()
                .onComplete(context.asyncAssertSuccess());
    }

    @Test
    public void validatorFailure(TestContext context) {
        TSV tsv = new TSV("gatt2111", 1)
                .validator(t -> Future.failedFuture("validator fail"))
                .processor(t -> Future.succeededFuture());
        tsv.validateAndProcess()
                .onComplete(context.asyncAssertFailure());
    }

    @Test
    public void processorFailure(TestContext context) {
        TSV tsv = new TSV("gatt2111", 1)
                .validator(t -> Future.succeededFuture())
                .processor(t -> Future.failedFuture("processor fail"));
        tsv.validateAndProcess()
                .onComplete(context.asyncAssertFailure());
    }

    @Test
    public void optionalValidatorSucceeds(TestContext context) {
        TSV tsv = new TSV("gatt2111", 1);
        tsv.validateAndProcess()
                .onComplete(context.asyncAssertSuccess());
    }

    @Test
    public void workingProcessorWorks(TestContext context) {
        TSV tsv = new TSV("gatt2111\nanother guy", 1)
                .processor(row -> Future.succeededFuture(toUpperCase(row)));
        tsv.validateAndProcess()
                .onComplete(context.asyncAssertSuccess(t -> context.assertEquals("GATT2111\nANOTHER GUY", tsv.text())));
    }

    private static String toUpperCase(TSV.Row row) throws TSVException {
        return row.getCol(0).toUpperCase();
    }

    @Test
    public void erroringProcessorWorks(TestContext context) {
        TSV tsv = new TSV("gatt2111\nanother guy", 1)
                .processor(row -> Future.failedFuture("deliberate fail"));
        tsv.validateAndProcess()
                .onComplete(context.asyncAssertFailure(error -> context.assertEquals("deliberate fail", error.getMessage())));
    }


}
