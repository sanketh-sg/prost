package de.unibamberg.dsam.group6.prost.service;

import de.unibamberg.dsam.group6.prost.util.Toast;
import java.util.ArrayList;
import java.util.List;
import javax.servlet.http.HttpSession;
import javax.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Handles toasts (messages delivered to user on each page refresh).
 * Toasts are on every page, and after they are rendered once, they
 * are deleted from the session.
 */
@Service
@RequiredArgsConstructor
public class UserErrorManager {
    private static final String TOAST_SESSION_KEY = "__errors";
    private static final String ADMIN_ERROR_KEY = "__admin_error";
    public static final String TOAST_TEMPLATE_KEY = "__toasts";
    private final HttpSession session;

    public List<Toast> getToasts() {
        var errors = this.session.getAttribute(TOAST_SESSION_KEY);
        if (errors == null) {
            var newToasts = new ArrayList<Toast>();
            this.session.setAttribute(TOAST_SESSION_KEY, newToasts);
            return newToasts;
        }
        return (List<Toast>) errors;
    }

    public List<Toast> getToastsAndRemove() {
        var toasts = this.getToasts();
        this.session.setAttribute(TOAST_SESSION_KEY, new ArrayList<Toast>());
        return toasts;
    }

    public void addToast(@NotNull Toast toast) {
        var currentToasts = this.getToasts();
        currentToasts.add(toast);
        this.session.setAttribute(TOAST_SESSION_KEY, currentToasts);
    }

    public void setAdminMessage(String errorText) {
        this.session.setAttribute(ADMIN_ERROR_KEY, errorText);
    }

    public String getAdminMessageAndRemove() {
        var message = (String) this.session.getAttribute(ADMIN_ERROR_KEY);
        this.session.setAttribute(ADMIN_ERROR_KEY, null);
        return message;
    }

    public void addAllToasts(Iterable<Toast> toasts) {
        toasts.forEach(this::addToast);
    }
}
