package com.kewei.parts.module.trace.controller;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kewei.parts.common.*;
import com.kewei.parts.infrastructure.storage.ChainTask;
import com.kewei.parts.module.trace.mapper.ChainTaskMapper;
import com.kewei.parts.module.parts.entity.CrossChainRecord;
import com.kewei.parts.module.parts.mapper.CrossChainRecordMapper;
import com.kewei.parts.security.CurrentActor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.Date;

@RestController
@RequestMapping("/api/admin/chain-tasks")
public class ChainTaskController {
    @Autowired private CurrentActor actor;
    @Autowired private ChainTaskMapper tasks;
    @Autowired private CrossChainRecordMapper relays;
    @GetMapping
    public Result<Page<ChainTask>> list(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize) {
        actor.requireRoles("admin");
        if(page<1 || pageSize<1 || pageSize>100) throw new BusinessException("分页参数不合法");
        return Result.success(tasks.selectPage(new Page<>(page,pageSize),new LambdaQueryWrapper<ChainTask>().orderByDesc(ChainTask::getId)));
    }
    @PostMapping("/{id}/retry") @Transactional(rollbackFor=Exception.class)
    public Result<Void> retry(@PathVariable Long id) {
        actor.requireRoles("admin");ChainTask task=tasks.selectById(id);
        if(task==null || !Integer.valueOf(-1).equals(task.getStatus())) throw new BusinessException("只能重试失败的任务");
        task.setStatus(0);task.setAttempts(0);task.setLastError("");task.setNextRetryAt(new Date());tasks.updateById(task);return Result.successMsg("已加入重试队列");
    }
    @PostMapping("/relay/{id}/retry") @Transactional(rollbackFor=Exception.class)
    public Result<Void> retryRelay(@PathVariable Long id) {
        actor.requireRoles("admin");CrossChainRecord record=relays.selectById(id);
        if(record==null || !Integer.valueOf(-1).equals(record.getStatus())) throw new BusinessException("只能重试失败的跨链任务");
        record.setStatus(0);record.setAttempts(0);record.setLastError("");record.setNextRetryAt(new Date());relays.updateById(record);return Result.successMsg("已加入跨链重试队列");
    }
}
