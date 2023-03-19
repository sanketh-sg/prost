package de.unibamberg.dsam.group6.prost.controller;

import de.unibamberg.dsam.group6.prost.entity.Bottle;
import de.unibamberg.dsam.group6.prost.repository.BottlesRepository;
import de.unibamberg.dsam.group6.prost.repository.CratesRepository;
import de.unibamberg.dsam.group6.prost.util.OffsetBasedPageRequest;
import java.util.Optional;
import javax.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class IndexController {
    private final BottlesRepository bottlesRepository;
    private final CratesRepository cratesRepository;

    @GetMapping("/")
    public String index(HttpSession session) {
        return "pages/index";
    }

    @GetMapping("/order_success")
    public String onAfterOrderSubmit() {
        return "pages/after_order_submission";
    }

    @GetMapping("/bottles")
    public String bottleCatalogue(
            @RequestParam Optional<Boolean> containsAlcohol, @RequestParam Optional<Integer> page, Model model) {
        var currentPage = 0;
        if (page.isPresent() && page.get() >= 0) {
            currentPage = page.get();
        }

        var pagable = new OffsetBasedPageRequest(currentPage * 9, 9);

        Page<Bottle> bottles;
        if (containsAlcohol.isPresent()) {
            if (containsAlcohol.get()) {
                bottles = this.bottlesRepository.findAllAlcoholic(pagable);
                model.addAttribute("max_page", (this.bottlesRepository.countAllAlcoholic() - 1) / 9);
            } else {
                bottles = this.bottlesRepository.findAllNonAlcoholic(pagable);
                model.addAttribute("max_page", (this.bottlesRepository.countAllNonAlcoholic() - 1) / 9);
            }
        } else {
            bottles = this.bottlesRepository.findAll(pagable);
            model.addAttribute("max_page", (this.bottlesRepository.count() - 1) / 9);
        }

        model.addAttribute("beverages", bottles.getContent());
        model.addAttribute("current_page", currentPage);
        return "pages/bottles";
    }

    @GetMapping("/crates")
    public String cratesCatalogue(@RequestParam Optional<Integer> page, Model model) {
        var currentPage = 0;
        if (page.isPresent() && page.get() >= 0) {
            currentPage = page.get();
        }

        var pagable = new OffsetBasedPageRequest(currentPage * 9, 9);
        var bottles = this.cratesRepository.findAll(pagable);

        model.addAttribute("beverages", bottles.getContent());
        model.addAttribute("max_page", (bottles.getTotalElements() - 1) / 9);
        model.addAttribute("current_page", currentPage);
        return "pages/crates";
    }
}
