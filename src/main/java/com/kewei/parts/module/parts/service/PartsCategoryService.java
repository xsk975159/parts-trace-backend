package com.kewei.parts.module.parts.service;

import com.kewei.parts.module.parts.entity.PartsCategory;

import java.util.List;

/**
 * 零部件分类服务接"
 */
public interface PartsCategoryService {

    List<PartsCategory> getAllCategories();

    List<PartsCategory> getCategoryTree();

    PartsCategory getCategoryById(Long categoryId);

    void createCategory(PartsCategory category);

    void updateCategory(PartsCategory category);

    void deleteCategory(Long categoryId);

    List<PartsCategory> buildCategoryTree(List<PartsCategory> categories);
}





