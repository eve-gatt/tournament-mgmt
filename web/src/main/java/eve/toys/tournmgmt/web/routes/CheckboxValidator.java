package eve.toys.tournmgmt.web.routes;

import io.vertx.ext.web.api.RequestParameter;
import io.vertx.ext.web.api.validation.impl.BooleanTypeValidator;

public class CheckboxValidator extends BooleanTypeValidator {

    public CheckboxValidator(Boolean defaultValue) {
        super(defaultValue);
    }

    @Override
    public RequestParameter isValidSingleParam(String value) {
        if (value.equalsIgnoreCase("on"))
            return RequestParameter.create(true);
        else
            return super.isValidSingleParam(value);
    }
}
