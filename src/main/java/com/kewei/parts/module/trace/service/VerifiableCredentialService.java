package com.kewei.parts.module.trace.service;

import com.kewei.parts.module.trace.entity.VerifiableCredential;
import com.kewei.parts.module.trace.entity.VcVerifyRecord;

import java.util.List;

/**
 * 可验证凭证服务接口
 */
public interface VerifiableCredentialService {

    /**
     * 签发生产证明
     */
    VerifiableCredential issueProductionCertificate(Long eventId);

    /**
     * 签发质检报告
     */
    VerifiableCredential issueQualityReport(Long eventId);

    /**
     * 签发流转证明
     */
    VerifiableCredential issueTransferCertificate(Long eventId);

    /**
     * 签发综合溯源凭证
     */
    VerifiableCredential issueComprehensiveCredential(Long partsId);

    VerifiableCredential issueUnitComprehensiveCredential(Long unitId);

    /**
     * 验证凭证
     */
    boolean verifyCredential(String vcId);

    /** 标准化验证结果 */
    Object verifyCredentialStandard(String vcId);

    /**
     * 撤销凭证
     */
    void revokeCredential(String vcId, String reason);

    /**
     * 获取零部件的所有凭证
     */
    List<VerifiableCredential> getPartsCredentials(Long partsId);

    /**
     * 获取事件的凭证
     */
    VerifiableCredential getEventCredential(Long eventId);

    /** 验证记录查询 */
    List<VcVerifyRecord> getVerifyRecords(String vcId);
}
