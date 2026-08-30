package de.unibamberg.dsam.group6.prost.util;

import java.util.Optional;

/**
 * Guards redirect targets that come from request parameters.
 *
 * <p>Concatenating a user-supplied {@code next} into {@code "redirect:" + next} lets an attacker
 * send the victim to another origin from a link that genuinely originates here — a phishing
 * primitive. Only same-site absolute paths are allowed through.
 */
public final class Redirects {
    private Redirects() {}

    public static String safe(Optional<String> next, String fallback) {
        var target = next.orElse("");
        if (!target.startsWith("/")) {
            // Absolute URLs, scheme-relative URLs and pseudo-schemes all fail here.
            return fallback;
        }
        if (target.startsWith("//") || target.startsWith("/\\")) {
            // Scheme-relative; browsers also normalise a backslash to a slash.
            return fallback;
        }
        if (containsControlCharacter(target)) {
            // Browsers strip tabs and newlines while parsing a URL, so "/\t/evil.example" would
            // be fetched as "//evil.example". The prefix checks above cannot see that, because
            // the string really does start with a single slash.
            return fallback;
        }
        return target;
    }

    private static boolean containsControlCharacter(String target) {
        for (var i = 0; i < target.length(); i++) {
            var c = target.charAt(i);
            if (c < 0x20 || c == 0x7F) {
                return true;
            }
        }
        return false;
    }
}
