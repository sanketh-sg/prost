package de.unibamberg.dsam.group6.prost.configuration;

import de.unibamberg.dsam.group6.prost.repository.UserRepository;
import de.unibamberg.dsam.group6.prost.service.UserErrorManager;
import de.unibamberg.dsam.group6.prost.util.Toast;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice(annotations = Controller.class)
@RequiredArgsConstructor
public class ControllerAdviceSetup {
    private final UserErrorManager errors;
    private final UserRepository userRepository;

    @ModelAttribute(name = UserErrorManager.TOAST_TEMPLATE_KEY)
    public List<Toast> getToasts() {
        return this.errors.getToastsAndRemove();
    }
}
