package de.unibamberg.dsam.group6.prost.controller;

import de.unibamberg.dsam.group6.prost.entity.Bottle;
import de.unibamberg.dsam.group6.prost.entity.Crate;
import de.unibamberg.dsam.group6.prost.repository.BottlesRepository;
import de.unibamberg.dsam.group6.prost.repository.CratesRepository;
import de.unibamberg.dsam.group6.prost.repository.OrdersRepository;
import de.unibamberg.dsam.group6.prost.repository.UserRepository;
import de.unibamberg.dsam.group6.prost.service.AdminActionsProvider;
import de.unibamberg.dsam.group6.prost.service.UserErrorManager;
import de.unibamberg.dsam.group6.prost.service.admin.VersionReader;
import de.unibamberg.dsam.group6.prost.util.Toast;
import de.unibamberg.dsam.group6.prost.util.exception.CallFailedException;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.Errors;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {
    private final UserErrorManager errors;
    private final AdminActionsProvider actions;
    private final BottlesRepository bottlesRepository;
    private final CratesRepository cratesRepository;
    private final OrdersRepository ordersRepository;
    private final UserRepository userRepository;
    private final VersionReader version;

    @GetMapping("")
    public String adminIndex(
            @RequestParam(name = "p") Optional<String> page, @RequestParam Optional<String> username, Model model) {
        model.addAttribute("actions", this.actions.getAnnotatedInstances());
        model.addAttribute("version", this.version.getVersion());

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

    @GetMapping("/action")
    public String runAction(
            @RequestParam(name = "a") Optional<String> action,
            @RequestParam Optional<Boolean> await,
            @RequestParam Optional<String> next) {
        if (action.isEmpty()) {
            return "redirect:" + next.orElse("/admin");
        }

        var a = action.get().split("::");
        if (a.length != 2) {
            return "redirect:" + next.orElse("/admin");
        }

        var instance = this.actions.getAnnotatedInstances().stream()
                .filter(i -> i.getInstanceName().equals(a[0]))
                .toList();
        if (instance.size() != 1) {
            return "redirect:" + next.orElse("/admin");
        }

        try {
            if (await.orElse(false)) {
                this.errors.addToast(
                        Toast.success(instance.get(0).callAndReturn(a[1]).get().toString()));
            } else {
                instance.get(0).call(a[1]);
            }
        } catch (CallFailedException | InterruptedException | ExecutionException e) {
            this.errors.addToast(Toast.error("Action failed: %s", e));
        }
        return "redirect:" + next.orElse("/admin");
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
        return "redirect:" + next.orElse("/admin");
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
        return "redirect:" + next.orElse("/admin");
    }
}
