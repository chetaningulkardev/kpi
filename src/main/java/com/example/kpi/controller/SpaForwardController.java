package com.example.kpi.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Forwards Angular client-side routes to index.html so deep links and page
 * refreshes work.
 * <p>
 * This used to be an explicit list of paths, which drifted out of step with the
 * router: every {@code /kpi/*} screen and {@code /notifications} were absent, so
 * reloading one of them fell through to the API and rendered a raw JSON error
 * instead of the application.
 * <p>
 * Matching on the shape of the request avoids that whole class of problem. A
 * client route is anything that is not the API, not the actuator, and does not
 * look like a file — the last check keeps a missing {@code .js} or {@code .css}
 * returning a genuine 404 rather than a copy of index.html, which would
 * otherwise reach the browser as a confusing syntax error.
 * <p>
 * Static assets never get here: the resource handler serves them first, and
 * this mapping only runs when nothing else matched.
 */
@Controller
public class SpaForwardController {

    @GetMapping({"/", "/{path:[^.]*}", "/{path:[^.]*}/{sub:[^.]*}", "/{path:[^.]*}/{sub:[^.]*}/{leaf:[^.]*}"})
    public String forwardToIndex(HttpServletRequest request) {
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }

        if (path.startsWith("/api/") || path.equals("/api")
                || path.startsWith("/actuator/") || path.equals("/actuator")) {
            // Not ours — let the normal handler chain answer.
            return "forward:/error";
        }

        return "forward:/index.html";
    }
}

