package com.carenest.nursing.service.impl;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.carenest.common.utils.DateUtils;
import com.carenest.common.utils.StringUtils;
import com.carenest.framework.web.service.TokenService;
import com.carenest.nursing.dto.UserLoginRequestDto;
import com.carenest.nursing.service.WechatService;
import com.carenest.nursing.vo.LoginVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.carenest.nursing.mapper.FamilyMemberMapper;
import com.carenest.nursing.domain.FamilyMember;
import com.carenest.nursing.service.IFamilyMemberService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

/**
 * 老人家属Service业务层处理
 * 
 * @author alexis
 * @date 2026-06-08
 */
@Service
public class FamilyMemberServiceImpl extends ServiceImpl<FamilyMemberMapper, FamilyMember> implements IFamilyMemberService
{
    @Autowired
    private FamilyMemberMapper familyMemberMapper;
    @Autowired
    private WechatService wechatService;
    @Autowired
    private TokenService tokenService;
    static List<String> DEFAULT_NICKNAME_PREFIX = ListUtil.of(
            "生活更美好",
            "大桔大利",
            "日富一日",
            "好柿开花",
            "柿柿如意",
            "一椰暴富",
            "大柚所为",
            "杨梅吐气",
            "天生荔枝"
    );

    /**
     * 查询老人家属
     * 
     * @param id 老人家属主键
     * @return 老人家属
     */
    @Override
    public FamilyMember selectFamilyMemberById(Long id)
    {
        return getById(id);
    }

    /**
     * 查询老人家属列表
     * 
     * @param familyMember 老人家属
     * @return 老人家属
     */
    @Override
    public List<FamilyMember> selectFamilyMemberList(FamilyMember familyMember)
    {
        return familyMemberMapper.selectFamilyMemberList(familyMember);
    }

    /**
     * 新增老人家属
     * 
     * @param familyMember 老人家属
     * @return 结果
     */
    @Override
    public int insertFamilyMember(FamilyMember familyMember)
    {
        return save(familyMember) ? 1 : 0;
    }

    /**
     * 修改老人家属
     * 
     * @param familyMember 老人家属
     * @return 结果
     */
    @Override
    public int updateFamilyMember(FamilyMember familyMember)
    {
        return updateById(familyMember) ? 1 : 0;
    }

    /**
     * 批量删除老人家属
     * 
     * @param ids 需要删除的老人家属主键
     * @return 结果
     */
    @Override
    public int deleteFamilyMemberByIds(Long[] ids)
    {
        return removeByIds(Arrays.asList(ids)) ? 1 : 0;
    }

    /**
     * 删除老人家属信息
     * 
     * @param id 老人家属主键
     * @return 结果
     */
    @Override
    public int deleteFamilyMemberById(Long id)
    {
        return removeById(id) ? 1 : 0;
    }

    /**
     * 小程序端登录
     * @param userLoginRequestDto
     * @return
     */
    @Override
    public LoginVo login(UserLoginRequestDto userLoginRequestDto) {
        // 1.调用微信api,根据code获取openId
        String openId = wechatService.getOpenid(userLoginRequestDto.getCode());

        // 2.根据openId查询用户
        FamilyMember familyMember = getOne(Wrappers.<FamilyMember>lambdaQuery(FamilyMember.class)
                .eq(FamilyMember::getOpenId, openId));

        // 3.如果用户为空，则将openId设置到对象中
        if (ObjectUtil.isEmpty(familyMember)) {
            familyMember = FamilyMember.builder().openId(openId).build();
        }
//        if (ObjectUtil.isEmpty(familyMember)) {
//            familyMember = new FamilyMember();
//            familyMember.setOpenId(openId);
//        }

        // 4.调用微信api获取用户绑定的手机号
        String phone = wechatService.getPhone(userLoginRequestDto.getPhoneCode());

        // 5.保存或修改用户
        saveOrUpdateFamilyMember(familyMember, phone);

        // 6.将用户id存入token,返回
        Map<String, Object> claims = new HashMap<>();
        claims.put("username", familyMember.getName());
        claims.put("userId", familyMember.getId());
        String token = tokenService.createMemberToken(claims);

        LoginVo loginVo = new LoginVo();
        loginVo.setToken(token);
        loginVo.setNickName(familyMember.getName());
        return loginVo;
    }
    /**
     * 保存或修改客户
     * @param member
     * @param phone
     */
    private void saveOrUpdateFamilyMember(FamilyMember member, String phone) {

        // 1.判断取到的手机号与数据库中保存的手机号不一样
        if(ObjectUtil.notEqual(phone, member.getPhone())) {
            // 修改手机号
            member.setPhone(phone);
        }
        // 2.判断id存在，则修改
        if (ObjectUtil.isNotEmpty(member.getId())) {
            updateById(member);
            return;
        }
        // 3.id不存在，保存新用户
        // 随机组装昵称，词组+手机号后四位
        String nickName = DEFAULT_NICKNAME_PREFIX.get((int) (Math.random() * DEFAULT_NICKNAME_PREFIX.size()))
                + StringUtils.substring(member.getPhone(), 7);
        member.setName(nickName);
        save(member);
    }


}
