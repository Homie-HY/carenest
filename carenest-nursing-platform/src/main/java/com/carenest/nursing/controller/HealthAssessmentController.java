package com.carenest.nursing.controller;

import java.util.List;
import javax.servlet.http.HttpServletResponse;

import com.carenest.common.core.domain.R;
import com.carenest.common.utils.PDFUtil;
import com.carenest.oss.AliyunOSSOperator;
import lombok.extern.slf4j.Slf4j;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.carenest.common.annotation.Log;
import com.carenest.common.core.controller.BaseController;
import com.carenest.common.core.domain.AjaxResult;
import com.carenest.common.enums.BusinessType;
import com.carenest.nursing.domain.HealthAssessment;
import com.carenest.nursing.service.IHealthAssessmentService;
import com.carenest.common.utils.poi.ExcelUtil;
import com.carenest.common.core.page.TableDataInfo;
import org.springframework.web.multipart.MultipartFile;

/**
 * 健康评估Controller
 * 
 * @author alexis
 * @date 2026-05-30
 */
@Slf4j
@Api("健康评估管理")
@RestController
@RequestMapping("/nursing/healthAssessment")
public class HealthAssessmentController extends BaseController
{
    @Autowired
    private IHealthAssessmentService healthAssessmentService;
    @Autowired
    private AliyunOSSOperator aliyunOSSOperator;
    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    /**
     * 查询健康评估列表
     */
    @ApiOperation("查询健康评估列表")
    @PreAuthorize("@ss.hasPermi('nursing:healthAssessment:list')")
    @GetMapping("/list")
    public TableDataInfo<List<HealthAssessment>> list(@ApiParam("查询条件对象") HealthAssessment healthAssessment)
    {
        startPage();
        List<HealthAssessment> list = healthAssessmentService.selectHealthAssessmentList(healthAssessment);
        return getDataTable(list);
    }

    /**
     * 导出健康评估列表
     */
    @ApiOperation("导出健康评估列表")
    @PreAuthorize("@ss.hasPermi('nursing:healthAssessment:export')")
    @Log(title = "健康评估", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(@ApiParam("导出的查询条件") HttpServletResponse response, HealthAssessment healthAssessment)
    {
        List<HealthAssessment> list = healthAssessmentService.selectHealthAssessmentList(healthAssessment);
        ExcelUtil<HealthAssessment> util = new ExcelUtil<HealthAssessment>(HealthAssessment.class);
        util.exportExcel(response, list, "健康评估数据");
    }

    /**
     * 查询健康评估
     */
    @ApiOperation("获取健康评估详细信息")
    @PreAuthorize("@ss.hasPermi('nursing:healthAssessment:query')")
    @GetMapping(value = "/{id}")
    public R<HealthAssessment> getInfo(@PathVariable("id") @ApiParam("健康评估ID") Long id)
    {
        return R.ok(healthAssessmentService.selectHealthAssessmentById(id));
    }

    /**
     * 新增健康评估
     */
    @ApiOperation("新增健康评估")
    @PreAuthorize("@ss.hasPermi('nursing:healthAssessment:add')")
    @Log(title = "健康评估", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody @ApiParam("新增的健康评估对象") HealthAssessment healthAssessment)
    {
        Long id = healthAssessmentService.insertHealthAssessment(healthAssessment);
        return success(id);
    }

    /**
     * 修改健康评估
     */
    @ApiOperation("修改健康评估")
    @PreAuthorize("@ss.hasPermi('nursing:healthAssessment:edit')")
    @Log(title = "健康评估", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody @ApiParam("修改的健康评估对象") HealthAssessment healthAssessment)
    {
        return toAjax(healthAssessmentService.updateHealthAssessment(healthAssessment));
    }

    /**
     * 删除健康评估
     */
    @ApiOperation("删除健康评估")
    @PreAuthorize("@ss.hasPermi('nursing:healthAssessment:remove')")
    @Log(title = "健康评估", businessType = BusinessType.DELETE)
	@DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable @ApiParam("要删除的健康评估ID") Long[] ids)
    {
        return toAjax(healthAssessmentService.deleteHealthAssessmentByIds(ids));
    }
    /**
     * 通用上传请求（单个）
     */
    @ApiOperation("上传体检报告")
    @PostMapping("/upload")
    public AjaxResult uploadFile(MultipartFile file, String idCardNo) throws Exception
    {
        try {
            // 验证文件是否为空
            if (file == null || file.isEmpty()) {
                return AjaxResult.error("请选择要上传的PDF文件");
            }
            
            // 验证文件大小
            if (file.getSize() > 10 * 1024 * 1024) { // 10MB
                return AjaxResult.error("文件大小不能超过10MB");
            }
            
            // 验证文件类型
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".pdf")) {
                return AjaxResult.error("请上传PDF格式的文件");
            }
            
            log.info("开始处理PDF文件: {}, 大小: {} bytes", originalFilename, file.getSize());
            
            // 上传到OSS
            String url = aliyunOSSOperator.upload(file.getBytes(), originalFilename);

            AjaxResult ajax = AjaxResult.success();
            ajax.put("url", url);
            ajax.put("fileName", url);
            ajax.put("originalFilename", originalFilename);
            
            // PDF文件内容读取为字符串
            String content = PDFUtil.pdfToString(file.getInputStream());
            
            // 检查PDF解析是否成功
            if (content == null || content.trim().isEmpty()) {
                log.warn("PDF解析结果为空，文件: {}", originalFilename);
                return AjaxResult.error("PDF文件解析失败，可能原因：\n1. 文件已损坏或不完整\n2. 文件不是有效的PDF格式\n3. PDF文件加密或受保护\n4. PDF文件为空或没有文本内容\n请重新上传正确的PDF文件");
            }
            
            log.info("PDF解析成功，提取文本长度: {}", content.length());
            
            // 临时存储到redis中
            redisTemplate.opsForHash().put("healthReport", idCardNo, content);

            return ajax;
        } catch (Exception e) {
            // 记录详细错误日志
            log.error("文件上传异常: {} - {}", e.getClass().getName(), e.getMessage(), e);
            return AjaxResult.error("文件上传失败: " + e.getMessage());
        }
    }
}
