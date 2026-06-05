package com.example.orderdemo.interfaces.rest;

import com.example.orderdemo.application.hotrank.BoostExposureService;
import com.example.orderdemo.application.hotrank.HotRankQueryService;
import com.example.orderdemo.application.hotrank.RankedContent;
import com.example.orderdemo.domain.hotrank.BoostExposureCommand;
import com.example.orderdemo.domain.hotrank.BoostExposureResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/hotrank")
public class HotRankController {

    private final HotRankQueryService queryService;
    private final BoostExposureService boostService;

    public HotRankController(HotRankQueryService queryService, BoostExposureService boostService) {
        this.queryService = queryService;
        this.boostService = boostService;
    }

    @GetMapping("/{region}/top")
    public ResponseEntity<List<RankedContent>> getTopK(
            @PathVariable String region,
            @RequestParam(defaultValue = "10") int k) {
        return ResponseEntity.ok(queryService.getTopK(region, k));
    }

    @PostMapping("/boost")
    public ResponseEntity<BoostExposureResult> boostExposure(@RequestBody Map<String, Object> body) {
        try {
            var cmd = new BoostExposureCommand(
                    (String) body.get("targetContentId"),
                    ((Number) body.get("weight")).intValue(),
                    (String) body.get("region"),
                    (String) body.get("idempotencyKey"),
                    (String) body.get("decisionSource")
            );
            BoostExposureResult result = boostService.execute(cmd);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.ok(BoostExposureResult.rejected(e.getMessage(), (String) body.get("idempotencyKey")));
        }
    }
}