package eve.toys.tournmgmt.web.stream;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

public class Widget {
    private final WidgetType widgetType;
    private final String label;
    private final Command command;

    public Widget(WidgetType widgetType, String label, Command command) {
        this.widgetType = widgetType;
        this.label = label;
        this.command = command;
    }

    public Future<JsonObject> fetch() {
        return command.fetchData();
    }

    public String widgetName() {
        return toCamel(label);
    }

    public boolean matchesWidgetName(String widgetName) {
        return toCamel(label).equals(widgetName);
    }

    private String toCamel(String label) {
        String[] split = label.split(" ");
        split[0] = split[0].substring(0, 1).toLowerCase() + split[0].substring(1);
        for (int i = 1; i < split.length; i++) {
            split[i] = split[i].substring(0, 1).toUpperCase() + split[i].substring(1);
        }
        return String.join("", split);
    }

    public WidgetType getWidgetType() {
        return widgetType;
    }

    public String getLabel() {
        return label;
    }

    public String getCustom(String customProperty) {
        return command.getCustom(customProperty);
    }
}
