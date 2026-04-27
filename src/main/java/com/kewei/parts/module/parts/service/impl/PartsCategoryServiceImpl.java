package com.kewei.parts.module.parts.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kewei.parts.common.BusinessException;
import com.kewei.parts.module.parts.entity.PartsCategory;
import com.kewei.parts.module.parts.mapper.PartsCategoryMapper;
import com.kewei.parts.module.parts.service.PartsCategoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class PartsCategoryServiceImpl implements PartsCategoryService {

    @Autowired
    private PartsCategoryMapper categoryMapper;

    @Override
    public List<PartsCategory> getAllCategories() {
        LambdaQueryWrapper<PartsCategory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PartsCategory::getStatus, 1);
        wrapper.orderByAsc(PartsCategory::getSort);
        return categoryMapper.selectList(wrapper);
    }

    @Override
    public List<PartsCategory> getCategoryTree() {
        return buildCategoryTree(getAllCategories());
    }

    @Override
    public PartsCategory getCategoryById(Long categoryId) {
        PartsCategory category = categoryMapper.selectById(categoryId);
        if (category == null) throw new BusinessException("分类不存在");
        return category;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createCategory(PartsCategory category) {
        LambdaQueryWrapper<PartsCategory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PartsCategory::getCategoryCode, category.getCategoryCode());
        if (categoryMapper.selectOne(wrapper) != null) {
            throw new BusinessException("分类编码已存在");
        }
        if (category.getStatus() == null) category.setStatus(1);
        if (category.getSort() == null) category.setSort(0);
        if (category.getParentId() == null) category.setParentId(0L);
        if (category.getLevel() == null) category.setLevel(1);
        if (categoryMapper.insert(category) <= 0) throw new BusinessException("创建分类失败");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateCategory(PartsCategory category) {
        if (categoryMapper.selectById(category.getId()) == null) throw new BusinessException("分类不存在");
        if (categoryMapper.updateById(category) <= 0) throw new BusinessException("更新分类失败");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCategory(Long categoryId) {
        if (categoryMapper.selectById(categoryId) == null) throw new BusinessException("分类不存在");
        LambdaQueryWrapper<PartsCategory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PartsCategory::getParentId, categoryId);
        if (categoryMapper.selectCount(wrapper) > 0) throw new BusinessException("该分类下有子分类，无法删除");
        if (categoryMapper.deleteById(categoryId) <= 0) throw new BusinessException("删除分类失败");
    }

    @Override
    public List<PartsCategory> buildCategoryTree(List<PartsCategory> categories) {
        List<PartsCategory> roots = categories.stream()
                .filter(c -> c.getParentId() == null || c.getParentId() == 0)
                .collect(Collectors.toList());
        roots.forEach(r -> r.setChildren(buildChildren(r.getId(), categories)));
        return roots;
    }

    private List<PartsCategory> buildChildren(Long parentId, List<PartsCategory> all) {
        List<PartsCategory> children = all.stream()
                .filter(c -> c.getParentId() != null && c.getParentId().equals(parentId))
                .collect(Collectors.toList());
        children.forEach(c -> c.setChildren(buildChildren(c.getId(), all)));
        return children;
    }
}
