package com.kewei.parts.module.parts.controller;

import com.kewei.parts.common.Result;
import com.kewei.parts.module.parts.entity.PartsCategory;
import com.kewei.parts.module.parts.service.PartsCategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 零部件分类控制器
 */
@Tag(name = "零部件分类管", description = "零部件分类相关接")
@RestController
@RequestMapping("/api/parts/category")
public class PartsCategoryController {

    @Autowired
    private PartsCategoryService categoryService;

    @Operation(summary = "获取分类列表")
    @GetMapping("/list")
    public Result<List<PartsCategory>> getCategoryList() {
        return Result.success(categoryService.getAllCategories());
    }

    @Operation(summary = "获取分类")
    @GetMapping("/tree")
    public Result<List<PartsCategory>> getCategoryTree() {
        return Result.success(categoryService.getCategoryTree());
    }

    @Operation(summary = "获取分类详情")
    @GetMapping("/{id}")
    public Result<PartsCategory> getCategoryById(@PathVariable Long id) {
        return Result.success(categoryService.getCategoryById(id));
    }

    @Operation(summary = "创建分类")
    @PostMapping("/create")
    public Result<Void> createCategory(@RequestBody PartsCategory category) {
        categoryService.createCategory(category);
        return Result.successMsg("分类创建成功");
    }

    @Operation(summary = "更新分类")
    @PutMapping("/update")
    public Result<Void> updateCategory(@RequestBody PartsCategory category) {
        categoryService.updateCategory(category);
        return Result.successMsg("分类更新成功");
    }

    @Operation(summary = "删除分类")
    @DeleteMapping("/delete/{id}")
    public Result<Void> deleteCategory(@PathVariable Long id) {
        categoryService.deleteCategory(id);
        return Result.successMsg("分类删除成功");
    }
}





