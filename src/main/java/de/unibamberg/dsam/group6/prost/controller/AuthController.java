package de.unibamberg.dsam.group6.prost.controller;

import de.unibamberg.dsam.group6.prost.entity.User;
import de.unibamberg.dsam.group6.prost.repository.UserRepository;
import de.unibamberg.dsam.group6.prost.service.UserErrorManager;
import de.unibamberg.dsam.group6.prost.util.RegistrationForm;
import de.unibamberg.dsam.group6.prost.util.Toast;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.security.Principal;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.Errors;
import org.springframework.web.bind.annotation.*;

@Controller
@RequiredArgsConstructor
public class AuthController {
    private final UserErrorManager errors;
    private final UserRepository userRepo;
    private final PasswordEncoder passwordEncoder;

    @GetMapping("/whoami")
    @ResponseBody
    @Profile("dev")
    public String whoami(Principal principal) {
        return principal == null ? "not logged in" : principal.getName();
    }

    @GetMapping("/login")
    public String loginPage(@RequestParam(required = false) String next, Model model) {
        model.addAttribute("next", next);
        return "pages/login";
    }

    @PostMapping("/logout")
    public String logout() {
        return "redirect:/";
    }

    @GetMapping("/register")
    public String registerPage(Principal principal, Model model) {
        if (principal != null) {
            return "redirect:/";
        }
        model.addAttribute("form", new RegistrationForm());
        return "pages/register";
    }

    @PostMapping("/register")
    public String register(
            HttpServletRequest req, @ModelAttribute("form") @Valid RegistrationForm form, Errors errors) {
        // Validation runs on the submitted form, before the password is encoded. Validating the
        // built entity instead meant @NotEmpty inspected a bcrypt string, which is never empty.
        if (this.userRepo.findUserByUsername(form.getUsername()).isPresent()) {
            errors.rejectValue("username", "duplicate", "This username is already in use. :/");
        }

        if (form.getBirthday() != null
                && form.getBirthday().isAfter(LocalDate.now().minusYears(16))) {
            errors.rejectValue("birthday", "tooYoung", "You are too young to join :)");
        }

        if (errors.hasErrors()) {
            return "pages/register";
        }

        var user = User.builder()
                .username(form.getUsername())
                .password(this.passwordEncoder.encode(form.getPassword()))
                .birthday(form.getBirthday())
                .build();

        this.userRepo.saveAndFlush(user);
        try {
            req.login(form.getUsername(), form.getPassword());
        } catch (ServletException e) {
            this.errors.addToast(Toast.notice("Failed to log in :/"));
        }

        return "redirect:/";
    }
}
