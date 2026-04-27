package com.kewei.parts.module.parts.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kewei.parts.common.Result;
import com.kewei.parts.module.parts.dto.PartsCreateDTO;
import com.kewei.parts.module.parts.dto.PartsQueryDTO;
import com.kewei.parts.module.parts.dto.PartsUpdateDTO;
import com.kewei.parts.module.parts.entity.PartsDid;
import com.kewei.parts.module.parts.entity.PartsInfo;
import com.kewei.parts.module.parts.service.PartsService;
import com.kewei.parts.module.parts.vo.PartsInfoVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;

/**
 * 零部件管理控制器
 */
@Tag(name = "零部件管", description = "零部件基础信息管理接口")
@RestController
@RequestMapping("/api/parts")
public class PartsController {

    @Autowired
    private PartsService partsService;

    /**
     * 创建零部"
     */
    @Operation(summary = "创建零部")
    @PostMapping("/create")
    public Result<PartsInfo> createParts(HttpServletRequest request, 
                                         @Validated @RequestBody PartsCreateDTO createDTO) {
        Long creatorId = (Long) request.getAttribute("userId");
        PartsInfo partsInfo = partsService.createParts(createDTO, creatorId);
        return Result.success("创建成功", partsInfo);
    }

    /**
     * 更新零部件信"
     */
    @Operation(summary = "更新零部件信")
    @PutMapping("/{partsId}")
    public Result<Void> updateParts(@PathVariable Long partsId, 
                                    @RequestBody PartsUpdateDTO updateDTO) {
        partsService.updateParts(partsId, updateDTO);
        return Result.successMsg("更新成功");
    }

    /**
     * 获取零部件详"
     */
    @Operation(summary = "获取零部件详")
    @GetMapping("/{partsId}")
    public Result<PartsInfoVO> getPartsInfo(@PathVariable Long partsId) {
        PartsInfoVO partsInfo = partsService.getPartsInfo(partsId);
        return Result.success(partsInfo);
    }

    /**
     * 根据批次号查询零部件
     */
    @Operation(summary = "根据批次号查询零部件")
    @GetMapping("/batch/{batchNumber}")
    public Result<PartsInfo> getPartsByBatchNumber(@PathVariable String batchNumber) {
        PartsInfo partsInfo = partsService.getPartsByBatchNumber(batchNumber);
        return Result.success(partsInfo);
    }

    /**
     * 分页查询零部件列表（GET方式，兼容前端）
     */
    @Operation(summary = "获取零部件列")
    @GetMapping("/list")
    public Result<Page<PartsInfoVO>> getPartsListGet(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String partsName,
            @RequestParam(required = false) String batchNumber,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Integer status) {
        PartsQueryDTO queryDTO = new PartsQueryDTO();
        queryDTO.setPageNum(page);
        queryDTO.setPageSize(pageSize);
        queryDTO.setPartsName(partsName);
        queryDTO.setBatchNumber(batchNumber);
        queryDTO.setCategoryId(categoryId);
        queryDTO.setStatus(status);
        Page<PartsInfoVO> result = partsService.getPartsList(queryDTO);
        return Result.success(result);
    }

    /**
     * 获取零部件详情（兼容前端 /api/parts/detail/{id}"
     */
    @Operation(summary = "获取零部件详")
    @GetMapping("/detail/{id}")
    public Result<PartsInfoVO> getPartsDetail(@PathVariable Long id) {
        PartsInfoVO partsInfo = partsService.getPartsInfo(id);
        return Result.success(partsInfo);
    }

    /**
     * 更新零部件（PUT /api/parts/update，兼容前端）
     */
    @Operation(summary = "更新零部")
    @PutMapping("/update")
    public Result<Void> updatePartsBody(@RequestBody PartsUpdateDTO updateDTO) {
        if (updateDTO.getId() == null) {
            throw new com.kewei.parts.common.BusinessException("零部件ID不能为空");
        }
        partsService.updateParts(updateDTO.getId(), updateDTO);
        return Result.successMsg("更新成功");
    }

    /**
     * 分页查询零部件列表（POST方式"
     */
    @Operation(summary = "分页查询零部件列")
    @PostMapping("/list")
    public Result<Page<PartsInfoVO>> getPartsList(@RequestBody PartsQueryDTO queryDTO) {
        Page<PartsInfoVO> page = partsService.getPartsList(queryDTO);
        return Result.success(page);
    }

    /**
     * 删除零部"
     */
    @Operation(summary = "删除零部")
    @DeleteMapping("/delete/{partsId}")
    public Result<Void> deletePartsById(@PathVariable Long partsId) {
        partsService.deleteParts(partsId);
        return Result.successMsg("删除成功");
    }

    /**
     * 删除零部件（兼容旧路径）
     */
    @Operation(summary = "删除零部")
    @DeleteMapping("/{partsId}")
    public Result<Void> deleteParts(@PathVariable Long partsId) {
        partsService.deleteParts(partsId);
        return Result.successMsg("删除成功");
    }

    /**
     * 归档零部"
     */
    @Operation(summary = "归档零部")
    @PutMapping("/{partsId}/archive")
    public Result<Void> archiveParts(@PathVariable Long partsId) {
        partsService.archiveParts(partsId);
        return Result.successMsg("归档成功");
    }

    /**
     * 生成零部件DID
     */
    @Operation(summary = "生成零部件DID")
    @PostMapping("/{partsId}/did")
    public Result<PartsDid> generatePartsDid(@PathVariable Long partsId) {
        PartsDid partsDid = partsService.generatePartsDid(partsId);
        return Result.success("DID生成成功", partsDid);
    }

    /**
     * 更新零部件状"
     */
    @Operation(summary = "更新零部件状")
    @PutMapping("/{partsId}/status")
    public Result<Void> updatePartsStatus(@PathVariable Long partsId, @RequestParam Integer status) {
        partsService.updatePartsStatus(partsId, status);
        return Result.successMsg("状态更新成");
    }
}





