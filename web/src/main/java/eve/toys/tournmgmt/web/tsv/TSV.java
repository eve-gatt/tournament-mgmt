package eve.toys.tournmgmt.web.tsv;

import io.vertx.core.impl.StringEscapeUtils;
import io.vertx.core.json.JsonArray;
import io.vertx.ext.web.api.validation.HTTPRequestValidationHandler;
import io.vertx.ext.web.api.validation.ParameterTypeValidator;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class TSV {
    public static final HTTPRequestValidationHandler VALIDATOR = HTTPRequestValidationHandler.create()
            .addFormParamWithCustomTypeValidator("tsv",
                    ParameterTypeValidator.createStringTypeValidator(null, 7, null, null),
                    true,
                    false);

    private static final String LINE_SPLIT = "[\\r\\n]+";
    private static final String COLUMN_SPLIT = "[\\t,]";

    private final String tsv;
    private final int columnCount;

    public TSV(String tsv, int columnCount) {
        this.tsv = tsv;
        this.columnCount = columnCount;
    }

    public JsonArray json() {
        return stream().collect(
                JsonArray::new,
                (a, r) -> {
                    JsonArray row = new JsonArray();
                    IntStream.range(0, columnCount).forEach(i -> row.add(r.getCol(i)));
                    a.add(row);
                },
                JsonArray::addAll);
    }

    public Stream<Row> stream() {
        return Arrays.stream(tsv.split(LINE_SPLIT))
                .map(String::trim)
                .filter(row -> !row.isEmpty())
                .map(Row::new);
    }

    public String text() {
        return tsv;
    }

    private static class TSVException extends RuntimeException {
        public TSVException(String msg) {
            super(msg);
        }
    }

    public class Row {
        private final String row;

        public Row(String row) {
            this.row = row;
        }

        public String getCol(int columnIndex) {
            try {
                return StringEscapeUtils.escapeJava(columns().get(columnIndex));
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }

        private List<String> columns() {
            List<String> columns = Arrays.stream(row.split(COLUMN_SPLIT))
                    .map(String::trim)
                    .collect(Collectors.toList());
            if (columns.size() != columnCount) {
                throw new TSVException("Expected " + columnCount + " columns in: " + row);
            }
            return columns;
        }
    }
}
