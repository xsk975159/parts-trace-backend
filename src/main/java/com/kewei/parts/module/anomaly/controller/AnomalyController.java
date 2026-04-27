package com.kewei.parts.module.anomaly.controller;

import com.kewei.parts.common.Result;
import com.kewei.parts.module.anomaly.entity.AnomalyAlert;
import com.kewei.parts.module.anomaly.entity.AnomalyRule;
import com.kewei.parts.module.anomaly.service.AnomalyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 异常检测控制器
 */
@Tag(name = "异常检测管理, description = 规则配置预警管理")
@RestController
@RequestMapping("/api/anomaly")
public class AnomalyController {

    @Autowired
    private AnomalyService anomalyService;

    // ==================== 规则管理 ====================

    @Operation(summary = "获取规则列表")
    @GetMapping("/rule/list")
    public Result<Object> getRuleList(
            @RequestParam(required = false) String ruleType,
            @RequestParam(required = false) Integer status) {
        List<AnomalyRule> list = anomalyService.getRuleList(ruleType, status);
        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("total", list.size());
        return Result.success(result);
    }

    @Operation(summary = "获取规则详情")
    @GetMapping("/rule/{id}")
    public Result<AnomalyRule> getRuleById(@PathVariable Long id) {
        return Result.success(anomalyService.getRuleById(id));
    }

    @Operation(summary = "创建规则")
    @PostMapping("/rule/create")
    public Result<Void> createRule(@RequestBody AnomalyRule rule) {
        anomalyService.createRule(rule);
        return Result.successMsg("规则创建成功");
    }

    @Operation(summary = "更新规则")
    @PutMapping("/rule/update")
    public Result<Void> updateRule(@RequestBody AnomalyRule rule) {
        anomalyService.updateRule(rule);
        return Result.successMsg("规则更新成功");
    }

    @Operation(summary = "删除规则")
    @DeleteMapping("/rule/delete/{id}")
    public Result<Void> deleteRule(@PathVariable Long id) {
        anomalyService.deleteRule(id);
        return Result.successMsg("规则删除成功");
    }

    // ==================== 预警管理 ====================

    @Operation(summary = "获取预警列表")
    @GetMapping("/alert/list")
    public Result<Object> getAlertList(
            @RequestParam(required = false) String alertLevel,
            @RequestParam(required = false) Integer status) {
        List<AnomalyAlert> list = anomalyService.getAlertList(alertLevel, status);
        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("total", list.size());
        return Result.success(result);
    }

    @Operation(summary = "处理预警")
    @PostMapping("/alert/handle")
    public Result<Void> handleAlert(@RequestBody Map<String, Object> body) {
        Long id = Long.valueOf(body.get("id").toString());
        String handler = body.getOrDefault("handler", "").toString();
        String handleResult = body.getOrDefault("handleResult", "").toString();
        anomalyService.handleAlert(id, handler, handleResult);
        return Result.successMsg("预警处理成功");
    }

    // ==================== 统计数据 ====================

    @Operation(summary = "获取统计数据")
    @GetMapping("/stats")
    public Result<Map<String, Object>> getStats() {
        return Result.success(anomalyService.getStats());
    }
}





