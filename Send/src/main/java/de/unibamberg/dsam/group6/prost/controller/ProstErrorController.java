package de.unibamberg.dsam.group6.prost.controller;

import javax.servlet.RequestDispatcher;
import javax.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class ProstErrorController implements ErrorController {

    @RequestMapping("/error")
    public String handleError(HttpServletRequest request, Model model) {
        int statusCode = 400;

        try {
            var status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
            statusCode = Integer.parseInt(status.toString());
        } catch (NumberFormatException ignore) {
        }

        model.addAttribute("statusCode", statusCode);

        return "pages/error";
    }
}
