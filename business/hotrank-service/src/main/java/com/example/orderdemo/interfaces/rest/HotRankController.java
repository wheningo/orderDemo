package com.example.orderdemo.interfaces.rest;

import com.example.orderdemo.application.hotrank.HotRankQueryService;
import com.example.orderdemo.application.hotrank.RankedContent;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/hotrank")
public class HotRankController {

    private final HotRankQueryService queryService;

    public HotRankController(HotRankQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/{region}/top")
    public ResponseEntity<List<RankedContent>> getTopK(
            @PathVariable String region,
            @RequestParam(defaultValue = "10") int k) {
        return ResponseEntity.ok(queryService.getTopK(region, k));
    }
}