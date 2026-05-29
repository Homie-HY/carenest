package com.carenest.nursing.task;

import com.carenest.nursing.service.IContractService;
import lombok.extern.log4j.Log4j;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component("contractTask")
@Slf4j
public class ContractTask {

    @Autowired
    private IContractService contractService;

    public void updateContractStatusTask() {
        contractService.updateContractStatus();
        log.info("定时更新合同状态成功！");
    }
}