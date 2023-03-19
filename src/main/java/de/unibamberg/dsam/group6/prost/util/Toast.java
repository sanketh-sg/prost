package de.unibamberg.dsam.group6.prost.util;

import java.io.Serializable;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Class provides functionality for informing user about practically anything.
 */
@Data
@AllArgsConstructor
public class Toast implements Serializable {
    public static final String TEMPLATE_ATTRIBUTE_NAME = "__toasts";

    public enum ToastLevel {
        INFO,
        SUCCESS,
        NOTICE,
        ERROR;
    }

    private ToastLevel level;
    private String message;

    public static Toast info(@NotNull String message, Object... formatVars) {
        return new Toast(ToastLevel.INFO, String.format(message, formatVars));
    }

    public static Toast success(@NotNull String message, Object... formatVars) {
        return new Toast(ToastLevel.SUCCESS, String.format(message, formatVars));
    }

    public static Toast notice(@NotNull String message, Object... formatVars) {
        return new Toast(ToastLevel.NOTICE, String.format(message, formatVars));
    }

    public static Toast error(@NotNull String message, Object... formatVars) {
        return new Toast(ToastLevel.ERROR, String.format(message, formatVars));
    }
}
