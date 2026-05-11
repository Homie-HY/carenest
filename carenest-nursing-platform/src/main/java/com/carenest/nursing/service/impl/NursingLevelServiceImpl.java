package com.carenest.nursing.service.impl;

import java.util.Arrays;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.carenest.common.utils.DateUtils;
import com.carenest.nursing.vo.NursingLevelVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.carenest.nursing.mapper.NursingLevelMapper;
import com.carenest.nursing.domain.NursingLevel;
import com.carenest.nursing.service.INursingLevelService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

/**
 * 护理等级Service业务层处理
 * 
 * @author alexis
 * @date 2025-06-02
 */
@Service
public class NursingLevelServiceImpl extends ServiceImpl<NursingLevelMapper, NursingLevel> implements INursingLevelService
{
    @Autowired
    private NursingLevelMapper nursingLevelMapper;

    /**
     * 查询护理等级
     * 
     * @param id 护理等级主键
     * @return 护理等级
     */
    @Override
    public NursingLevel selectNursingLevelById(Long id)
    {
        return getById(id);
    }

    /**
     * 查询护理等级列表
     * 
     * @param nursingLevel 护理等级
     * @return 护理等级
     */
    @Override
    public List<NursingLevel> selectNursingLevelList(NursingLevel nursingLevel)
    {
        return nursingLevelMapper.selectNursingLevelList(nursingLevel);
    }

    /**
     * 新增护理等级
     * 
     * @param nursingLevel 护理等级
     * @return 结果
     */
    @Override
    public int insertNursingLevel(NursingLevel nursingLevel)
    {
        return save(nursingLevel) ? 1 : 0;
    }

    /**
     * 修改护理等级
     * 
     * @param nursingLevel 护理等级
     * @return 结果
     */
    @Override
    public int updateNursingLevel(NursingLevel nursingLevel)
    {
        return updateById(nursingLevel) ? 1 : 0;
    }

    /**
     * 批量删除护理等级
     * 
     * @param ids 需要删除的护理等级主键
     * @return 结果
     */
    @Override
    public int deleteNursingLevelByIds(Long[] ids)
    {
        return removeByIds(Arrays.asList(ids)) ? 1 : 0;
    }

    /**
     * 删除护理等级信息
     * 
     * @param id 护理等级主键
     * @return 结果
     */
    @Override
    public int deleteNursingLevelById(Long id)
    {
        return removeById(id) ? 1 : 0;
    }

    /**
     * 查询护理等级Vo列表
     *
     * @param nursingLevel 条件
     * @return 结果
     */
    @Override
    public List<NursingLevelVo> selectNursingLevelVoList(NursingLevel nursingLevel) {
        return nursingLevelMapper.selectNursingLevelVoList(nursingLevel);
    }

    /**
     * 查询所有护理等级
     *
     * @return 列表
     */
    @Override
    public List<NursingLevel> listAll() {
        LambdaQueryWrapper<NursingLevel> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(NursingLevel::getStatus, 1);
        return list(queryWrapper);
    }
}
