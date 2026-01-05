package com.sales.controller;

import com.sales.service.DemoDataService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/admin")
public class AdminDemoController {

    @Autowired
    private DemoDataService demoDataService;

    @PostMapping("/init-demo")
    public ResponseEntity<?> initDemo() {
        try {
            DemoDataService.InitResult result = demoDataService.initDemoData();
            return ResponseEntity.ok(result);
        } catch (IOException e) {
            log.error("Failed to init demo data", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "init-demo failed",
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("Unexpected error when init demo data", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "init-demo unexpected error",
                    "message", e.getMessage()
            ));
        }
    }
}
