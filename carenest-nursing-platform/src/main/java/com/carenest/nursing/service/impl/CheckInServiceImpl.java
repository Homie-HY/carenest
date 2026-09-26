package com.carenest.nursing.service.impl;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.ObjectUtils;
import com.carenest.common.exception.base.BaseException;
import com.carenest.common.utils.CodeGenerator;
import com.carenest.common.utils.DateUtils;
import com.carenest.nursing.domain.*;
import com.carenest.nursing.dto.CheckInApplyDto;
import com.carenest.nursing.dto.CheckInElderDto;
import com.carenest.nursing.mapper.*;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.carenest.nursing.service.ICheckInService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.transaction.annotation.Transactional;

/**
 * 入住Service业务层处理
 * 
 * @author Homie
 * @date 2026-05-07
 */
@Service
public class CheckInServiceImpl extends ServiceImpl<CheckInMapper, CheckIn> implements ICheckInService
{
    @Autowired
    private CheckInMapper checkInMapper;

    @Autowired
    private ElderMapper elderMapper;
    @Autowired
    private BedMapper bedMapper;
    @Autowired
    private ContractMapper contractMapper;
    @Autowired
    private CheckInConfigMapper checkInConfigMapper;

    /**
     * 查询入住
     * 
     * @param id 入住主键
     * @return 入住
     */
    @Override
    public CheckIn selectCheckInById(Long id)
    {
        return getById(id);
    }

    /**
     * 查询入住列表
     * 
     * @param checkIn 入住
     * @return 入住
     */
    @Override
    public List<CheckIn> selectCheckInList(CheckIn checkIn)
    {
        return checkInMapper.selectCheckInList(checkIn);
    }

    /**
     * 新增入住
     * 
     * @param checkIn 入住
     * @return 结果
     */
    @Override
    public int insertCheckIn(CheckIn checkIn)
    {
        return save(checkIn) ? 1 : 0;
    }

    /**
     * 修改入住
     * 
     * @param checkIn 入住
     * @return 结果
     */
    @Override
    public int updateCheckIn(CheckIn checkIn)
    {
        return updateById(checkIn) ? 1 : 0;
    }

    /**
     * 批量删除入住
     * 
     * @param ids 需要删除的入住主键
     * @return 结果
     */
    @Override
    public int deleteCheckInByIds(Long[] ids)
    {
        return removeByIds(Arrays.asList(ids)) ? 1 : 0;
    }

    /**
     * 删除入住信息
     * 
     * @param id 入住主键
     * @return 结果
     */
    @Override
    public int deleteCheckInById(Long id)
    {
        return removeById(id) ? 1 : 0;
    }

    /**
     * 入住申请
     * @param checkInApplyDto
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void apply(CheckInApplyDto checkInApplyDto) {
        LambdaQueryWrapper<Elder> elderQueryWrapper = new LambdaQueryWrapper<>();
        elderQueryWrapper.eq(Elder::getIdCardNo, checkInApplyDto.getCheckInElderDto().getIdCardNo());
        elderQueryWrapper.eq(Elder::getStatus, 1);
        Elder elder =  elderMapper.selectOne(elderQueryWrapper);
        if(ObjectUtils.isNotEmpty(elder)) {
            throw new BaseException("老人已入住");
        }

        // 更新床位的状态  已入住
        Bed bed = bedMapper.selectById(checkInApplyDto.getCheckInConfigDto().getBedId());
        bed.setBedStatus(1);
        bedMapper.updateById(bed);

        // 新增或更新老人基本信息
       elder = insertOrUpdateElder(bed, checkInApplyDto.getCheckInElderDto());

        // 生成合同编号
        String contractNo = "HT" + CodeGenerator.generateContractNumber();

        // 新增签约办理
        insertContract(contractNo, elder, checkInApplyDto);

        // 新增入住信息
        CheckIn checkIn = insertCheckInInfo(elder, checkInApplyDto);

        // 新增入住配置信息
        insertCheckInConfig(checkIn.getId(), checkInApplyDto);
    }

    /**
     * 新增入住配置信息
     * @param id
     * @param checkInApplyDto
     */
    private void insertCheckInConfig(Long id, CheckInApplyDto checkInApplyDto) {
        CheckInConfig checkInConfig = new CheckInConfig();
        checkInConfig.setCheckInId(id);
        BeanUtils.copyProperties(checkInApplyDto.getCheckInConfigDto(), checkInConfig);
        checkInConfigMapper.insert(checkInConfig);
    }

    /**
     * 新增入住信息
     * @param elder
     * @param checkInApplyDto
     */
    private CheckIn insertCheckInInfo(Elder elder, CheckInApplyDto checkInApplyDto) {
        CheckIn checkIn = new CheckIn();
        checkIn.setElderId(elder.getId());
        checkIn.setElderName(elder.getName());
        checkIn.setIdCardNo(elder.getIdCardNo());
        checkIn.setNursingLevelName(checkInApplyDto.getCheckInConfigDto().getNursingLevelName());
        checkIn.setStartDate(checkInApplyDto.getCheckInConfigDto().getStartDate());
        checkIn.setEndDate(checkInApplyDto.getCheckInConfigDto().getEndDate());
        checkIn.setBedNumber(elder.getBedNumber());
        checkIn.setRemark(JSON.toJSONString(checkInApplyDto.getElderFamilyDtoList()));
        checkIn.setStatus(0);
        checkInMapper.insert(checkIn);
        return checkIn;
    }

    /**
     * 新增签约办理
     * @param elder
     * @param checkInApplyDto
     */
    private void insertContract(String contractNo, Elder elder, CheckInApplyDto checkInApplyDto) {
        Contract contract = new Contract();
        BeanUtils.copyProperties(checkInApplyDto.getCheckInContractDto(), contract);
        contract.setContractNumber(contractNo);
        contract.setElderId(elder.getId());
        contract.setElderName(elder.getName());

        //获取入住开始时间和入住结束时间
        LocalDateTime checkInStartTime = checkInApplyDto.getCheckInConfigDto().getStartDate();
        LocalDateTime checkInEndTime = checkInApplyDto.getCheckInConfigDto().getEndDate();
        Integer status = checkInStartTime.isAfter(LocalDateTime.now()) ? 0 : 1;
        contract.setStatus(status);
        contract.setStartDate(checkInStartTime);
        contract.setEndDate(checkInEndTime);
        contractMapper.insert(contract);
    }

    /**
     * 新增或更新老人信息
     * @param bed
     * @param checkInElderDto
     */
    private Elder insertOrUpdateElder(Bed bed, CheckInElderDto checkInElderDto) {
        //准备一个Elder对象
        Elder elder = new Elder();
        BeanUtils.copyProperties(checkInElderDto, elder);
        elder.setBedId(bed.getId());
        elder.setBedNumber(bed.getBedNumber());
        elder.setStatus(1);
        //查询老人信息
        LambdaQueryWrapper<Elder> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(Elder::getIdCardNo, elder.getIdCardNo());
        lambdaQueryWrapper.notIn(Elder::getStatus, 1, 4);
        Elder elderInDb = elderMapper.selectOne(lambdaQueryWrapper);
        if(ObjectUtils.isNotEmpty(elderInDb)) {
            //修改
            elder.setId(elderInDb.getId());
            elderMapper.updateById(elder);
        } else {
            //新增
            elder.setCreateTime(DateUtils.getNowDate());
            elderMapper.insert(elder);
        }
        return elder;
    }
}
