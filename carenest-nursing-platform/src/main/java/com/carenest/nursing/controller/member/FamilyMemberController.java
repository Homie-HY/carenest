package com.carenest.nursing.controller.member;

import com.carenest.common.core.controller.BaseController;
import com.carenest.common.core.domain.AjaxResult;
import com.carenest.nursing.dto.UserLoginRequestDto;
import com.carenest.nursing.service.IFamilyMemberService;
import com.carenest.nursing.vo.LoginVo;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 老人家属Controller
 * 
 * @author Homie
 * @date 2026-06-08
 */
@Api("老人家属管理")
@RestController
@RequestMapping("/member/user")
public class FamilyMemberController extends BaseController {
    @Autowired
    private IFamilyMemberService familyMemberService;

    @PostMapping("/login")
    @ApiOperation("小程序登录")
    public AjaxResult login(@RequestBody UserLoginRequestDto dto) {
        LoginVo loginVo = familyMemberService.login(dto);
        return success(loginVo);
    }

}
