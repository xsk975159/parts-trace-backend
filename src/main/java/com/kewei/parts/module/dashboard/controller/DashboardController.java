package com.kewei.parts.module.dashboard.controller;

import com.kewei.parts.common.Result;
import com.kewei.parts.module.anomaly.service.AnomalyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 仪表板统计控制器
 */
@Tag(name = "仪表", description = "仪表板统计数据接")
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    @Autowired
    private AnomalyService anomalyService;

    /**
     * 获取仪表板统计数"
     */
    @Operation(summary = "获取仪表板统计数")
    @GetMapping("/stats")
    public Result<Map<String, Object>> getStats() {
        Map<String, Object> stats = anomalyService.getStats();
        return Result.success(stats);
    }
}





