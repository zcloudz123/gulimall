package com.gulimall.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gulimall.common.utils.PageUtils;
import com.gulimall.common.utils.Query;
import com.gulimall.product.dao.CategoryDao;
import com.gulimall.product.entity.CategoryEntity;
import com.gulimall.product.service.CategoryBrandRelationService;
import com.gulimall.product.service.CategoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;


@Service("categoryService")
public class CategoryServiceImpl extends ServiceImpl<CategoryDao, CategoryEntity> implements CategoryService {

    @Autowired
    private CategoryBrandRelationService categoryBrandRelationService;

    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        IPage<CategoryEntity> page = this.page(
                new Query<CategoryEntity>().getPage(params),
                new QueryWrapper<CategoryEntity>()
        );

        return new PageUtils(page);
    }

    @Override
    public List<CategoryEntity> listWithTree() {

        List<CategoryEntity> entities = baseMapper.selectList(null);

        List<CategoryEntity> level1Menus = entities.stream()
                .filter(categoryEntity -> categoryEntity.getParentCid() == 0)
                .map((menu) -> { menu.setChildren(getChildrens(menu,entities));return menu;})
                .sorted((menu1,menu2) -> {return (menu1.getSort() == null?0:menu1.getSort()) - (menu2.getSort() == null?0:menu2.getSort());})
                .collect(Collectors.toList());
        return level1Menus;

    }

    @Override
    public void removeMenusByIds(List<Long> asList) {
        //TODO 1.检查当前删除的菜单，是否被别的地方引用
        baseMapper.deleteBatchIds(asList);
    }

    @Override
    public Long[] findCategoryPath(Long catelogId) {
        List<Long> paths = new ArrayList<>();

        //迭代收集父节点id
        CategoryEntity categoryEntity = this.getById(catelogId);
        while(categoryEntity.getParentCid() != 0){
            paths.add(categoryEntity.getCatId());
            categoryEntity = this.getById(categoryEntity.getParentCid());
        }
        paths.add(categoryEntity.getCatId());

        Collections.reverse(paths);

        return paths.toArray(new Long[]{});
    }

    @Transactional
    @Override
    public void updateCascade(CategoryEntity category) {
        this.updateById(category);
        categoryBrandRelationService.updateCategoryName(category.getCatId(),category.getName());
    }

    private List<CategoryEntity> getChildrens(CategoryEntity entity,List<CategoryEntity> entities){
        return entities.stream()
                .filter(entity1 -> entity1.getParentCid().equals(entity.getCatId()))
                .map((menu) -> { menu.setChildren(getChildrens(menu,entities));return menu;})
                .sorted((menu1,menu2) -> {return (menu1.getSort() == null?0:menu1.getSort()) - (menu2.getSort() == null?0:menu2.getSort());})
                .collect(Collectors.toList());
    }

}