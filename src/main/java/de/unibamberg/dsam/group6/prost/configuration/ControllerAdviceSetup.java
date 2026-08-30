package de.unibamberg.dsam.group6.prost.configuration;

import de.unibamberg.dsam.group6.prost.service.UserErrorManager;
import de.unibamberg.dsam.group6.prost.util.Toast;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice(annotations = Controller.class)
@RequiredArgsConstructor
public class ControllerAdviceSetup {
    private final UserErrorManager errors;

    @ModelAttribute(name = UserErrorManager.TOAST_TEMPLATE_KEY)
    public List<Toast> getToasts() {
        return this.errors.getToastsAndRemove();
    }

    /**
     * Current request path, e.g. {@code /bottles}.
     *
     * <p>Thymeleaf 3.1 removed the {@code #request}, {@code #session},
     * {@code #servletContext} and {@code #response} expression objects, so templates
     * can no longer reach the request directly. Exposing what they actually need as
     * model attributes is the replacement Thymeleaf recommends.
     */
    @ModelAttribute("currentUri")
    public String currentUri(HttpServletRequest request) {
        return request.getRequestURI();
    }

    /**
     * Current request path including query string, e.g. {@code /bottles?page=2}.
     * Used by forms that need to send the user back where they came from.
     */
    @ModelAttribute("currentUrl")
    public String currentUrl(HttpServletRequest request) {
        var query = request.getQueryString();
        return query == null ? request.getRequestURI() : request.getRequestURI() + "?" + query;
    }
}
