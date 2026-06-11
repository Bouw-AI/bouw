package com.example.integration.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * SPA fallback controller for React client-side routing.
 *
 * All frontend routes defined here forward to index.html so React Router
 * (or state-based routing) can handle them. API routes under /api/ are
 * excluded — they are handled by their own controllers.
 */
@Controller
public class SpaController {

    @RequestMapping(value = {
            "/",
            "/chat/**",
            "/hall/**",
            "/settings/**",
            "/summon/**"
    })
    public String forwardToIndex() {
        return "forward:/index.html";
    }
}