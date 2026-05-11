package com.carenest.nursing.service.impl;

import java.util.Arrays;
import java.util.List;
import com.carenest.common.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.carenest.nursing.mapper.ContractMapper;
import com.carenest.nursing.domain.Contract;
import com.carenest.nursing.service.IContractService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

/**
 * 合同Service业务层处理
 * 
 * @author alexis
 * @date 2026-05-07
 */
@Service
public class ContractServiceImpl extends ServiceImpl<ContractMapper, Contract> implements IContractService
{
    @Autowired
    private ContractMapper contractMapper;

    /**
     * 查询合同
     * 
     * @param id 合同主键
     * @return 合同
     */
    @Override
    public Contract selectContractById(Long id)
    {
        return getById(id);
    }

    /**
     * 查询合同列表
     * 
     * @param contract 合同
     * @return 合同
     */
    @Override
    public List<Contract> selectContractList(Contract contract)
    {
        return contractMapper.selectContractList(contract);
    }

    /**
     * 新增合同
     * 
     * @param contract 合同
     * @return 结果
     */
    @Override
    public int insertContract(Contract contract)
    {
        return save(contract) ? 1 : 0;
    }

    /**
     * 修改合同
     * 
     * @param contract 合同
     * @return 结果
     */
    @Override
    public int updateContract(Contract contract)
    {
        return updateById(contract) ? 1 : 0;
    }

    /**
     * 批量删除合同
     * 
     * @param ids 需要删除的合同主键
     * @return 结果
     */
    @Override
    public int deleteContractByIds(Long[] ids)
    {
        return removeByIds(Arrays.asList(ids)) ? 1 : 0;
    }

    /**
     * 删除合同信息
     * 
     * @param id 合同主键
     * @return 结果
     */
    @Override
    public int deleteContractById(Long id)
    {
        return removeById(id) ? 1 : 0;
    }
}
