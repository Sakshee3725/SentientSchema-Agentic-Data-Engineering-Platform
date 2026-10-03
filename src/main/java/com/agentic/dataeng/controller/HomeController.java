package com.agentic.dataeng.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HomeController {

    @GetMapping("/api/health")
    public String home() {
        return "Agentic Data Engineering & Self-Healing Schema Evolution Platform is running!";
    }
}
