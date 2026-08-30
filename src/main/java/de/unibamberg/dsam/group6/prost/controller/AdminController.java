package de.unibamberg.dsam.group6.prost.controller;

import de.unibamberg.dsam.group6.prost.entity.Bottle;
import de.unibamberg.dsam.group6.prost.entity.Crate;
import de.unibamberg.dsam.group6.prost.repository.BottlesRepository;
import de.unibamberg.dsam.group6.prost.repository.CratesRepository;
import de.unibamberg.dsam.group6.prost.repository.OrdersRepository;
import de.unibamberg.dsam.group6.prost.repository.UserRepository;
import de.unibamberg.dsam.group6.prost.service.UserErrorManager;
import de.unibamberg.dsam.group6.prost.service.admin.DatabaseLoader;
import de.unibamberg.dsam.group6.prost.service.admin.VersionReader;
import de.unibamberg.dsam.group6.prost.util.Redirects;
import de.unibamberg.dsam.group6.prost.util.Toast;
import jakarta.validation.Valid;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.Errors;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
@Slf4j
public class AdminController {
    private final UserErrorManager errors;
    private final DatabaseLoader databaseLoader;
    private final BottlesRepository bottlesRepository;
    private final CratesRepository cratesRepository;
    private final OrdersRepository ordersRepository;
    private final UserRepository userRepository;
    private final VersionReader version;

    @GetMapping("")
    public String adminIndex(
            @RequestParam(name = "p") Optional<String> page, @RequestParam Optional<String> username, Model model) {
        model.addAttribute("version", this.version.getVersion());

        // Thymeleaf 3.1 removed #request, so the templates can no longer read these
        // query parameters themselves. They are already bound as method arguments.
        model.addAttribute("selectedPanel", page.orElse(""));
        model.addAttribute("selectedUsername", username.orElse(""));

        if (page.isPresent() && page.get().equals("orders")) {
            model.addAttribute("all_users", this.userRepository.getAllUsernamesHavingOrders());
            if (!username.orElse("").equals("")) {
                model.addAttribute("orders", this.ordersRepository.findAllByUser_username(username.get()));
            } else {
                model.addAttribute("orders", this.ordersRepository.findAll());
            }
        }

        return "pages/admin";
    }

    @PostMapping("/import/users")
    public String importUsers(@RequestParam Optional<String> next) {
        return this.runAction(this.databaseLoader::importUsers, next);
    }

    @PostMapping("/import/bottles")
    public String importBottles(@RequestParam Optional<String> next) {
        return this.runAction(this.databaseLoader::importBottles, next);
    }

    @PostMapping("/import/crates")
    public String importCrates(@RequestParam Optional<String> next) {
        return this.runAction(this.databaseLoader::importCrates, next);
    }

    @PostMapping("/import/all")
    public String importAll(@RequestParam Optional<String> next) {
        return this.runAction(this.databaseLoader::importAll, next);
    }

    @PostMapping("/clear")
    public String clearDatabase(@RequestParam Optional<String> next) {
        return this.runAction(this.databaseLoader::clearDatabase, next);
    }

    /**
     * Runs one seed action, reporting the outcome as a toast.
     *
     * <p>These were previously one GET endpoint that resolved a method by name from a query
     * parameter and invoked it reflectively, which left every destructive operation pre-fetchable
     * and outside CSRF protection.
     */
    private String runAction(AdminAction action, Optional<String> next) {
        try {
            this.errors.addToast(Toast.success(action.run()));
        } catch (Exception e) {
            log.warn("Admin action failed", e);
            this.errors.addToast(Toast.error("Action failed: %s", e.getMessage()));
        }
        return "redirect:" + Redirects.safe(next, "/admin");
    }

    @FunctionalInterface
    private interface AdminAction {
        String run() throws Exception;
    }

    @GetMapping("/form")
    public String renderForm(@RequestParam Optional<String> type, Model model) {
        if (type.isEmpty()) {
            return "pages/admin_form_page";
        }

        if (type.get().equals("bottle")) {
            model.addAttribute("bottle", new Bottle());
        } else if (type.get().equals("crate")) {
            model.addAttribute("crate", new Crate());
            model.addAttribute("allBottles", this.bottlesRepository.findAll());
        }

        return "pages/admin_form_page";
    }

    @PostMapping("/addBottle")
    public String addBottle(@RequestParam Optional<String> next, @ModelAttribute @Valid Bottle bottle, Errors errors) {
        if (errors.hasErrors()) {
            errors.getAllErrors().forEach(e -> {
                this.errors.addToast(
                        Toast.error("%s: %s", (e.getCodes() == null ? "" : e.getCodes()[1]), e.getDefaultMessage()));
            });
        } else {
            var added = this.bottlesRepository.save(bottle);
            this.errors.addToast(Toast.success("%s added successfully.", added.getName()));
        }
        return "redirect:" + Redirects.safe(next, "/admin");
    }

    @PostMapping("/addCrate")
    public String addCrate(@RequestParam Optional<String> next, @ModelAttribute @Valid Crate crate, Errors errors) {
        if (errors.hasErrors()) {
            errors.getAllErrors().forEach(e -> {
                this.errors.addToast(
                        Toast.error("%s: %s", (e.getCodes() == null ? "" : e.getCodes()[1]), e.getDefaultMessage()));
            });
        } else {
            var added = this.cratesRepository.save(crate);
            this.errors.addToast(Toast.success("%s added successfully.", added.getName()));
        }
        return "redirect:" + Redirects.safe(next, "/admin");
    }
}
