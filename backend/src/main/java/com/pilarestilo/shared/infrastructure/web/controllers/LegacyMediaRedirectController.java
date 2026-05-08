package com.pilarestilo.shared.infrastructure.web.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
public class LegacyMediaRedirectController {

    @GetMapping("/api/media/hero-left.png")
    public ResponseEntity<Void> redirectLegacyHeroLeft() {
        return ResponseEntity.status(HttpStatus.MOVED_PERMANENTLY)
                .location(URI.create("/api/media/hero-models/hero-left.png"))
                .build();
    }

    @GetMapping("/api/media/hero-right.png")
    public ResponseEntity<Void> redirectLegacyHeroRight() {
        return ResponseEntity.status(HttpStatus.MOVED_PERMANENTLY)
                .location(URI.create("/api/media/hero-models/hero-right.png"))
                .build();
    }
}
