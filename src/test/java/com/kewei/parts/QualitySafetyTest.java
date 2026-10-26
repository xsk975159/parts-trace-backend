package com.kewei.parts;
import com.kewei.parts.common.BusinessException;
import com.kewei.parts.infrastructure.storage.AuditRecorder;
import com.kewei.parts.module.anomaly.entity.*;
import com.kewei.parts.module.anomaly.mapper.*;
import com.kewei.parts.module.anomaly.service.QualitySafetyService;
import com.kewei.parts.module.parts.entity.*;
import com.kewei.parts.module.parts.mapper.*;
import com.kewei.parts.module.trace.entity.*;
import com.kewei.parts.module.trace.mapper.*;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class QualitySafetyTest {
    QualitySafetyService service;PartsDidMapper dids;AnomalyAlertMapper alerts;TraceQualityMapper qualities;TraceEventMapper events;
    TraceEvent event;TraceQuality quality;PartsDid a,b;
    @BeforeEach void setup() {
        service=new QualitySafetyService();dids=mock(PartsDidMapper.class);alerts=mock(AnomalyAlertMapper.class);qualities=mock(TraceQualityMapper.class);events=mock(TraceEventMapper.class);
        PartsInfoMapper parts=mock(PartsInfoMapper.class);AnomalyRuleMapper rules=mock(AnomalyRuleMapper.class);
        Object[][] deps={{"didMapper",dids},{"alertMapper",alerts},{"qualityMapper",qualities},{"eventMapper",events},{"partsMapper",parts},{"ruleMapper",rules},{"audits",mock(AuditRecorder.class)}};
        for(Object[] dep:deps)ReflectionTestUtils.setField(service,(String)dep[0],dep[1]);
        PartsInfo part=new PartsInfo();part.setId(1L);part.setPartsCode("P1");when(parts.selectById(1L)).thenReturn(part);
        event=new TraceEvent();event.setId(1L);event.setPartsId(1L);event.setUnitId(2L);event.setEventCode("E1");quality=new TraceQuality();
        a=new PartsDid();a.setId(1L);a.setDidStatus(1);b=new PartsDid();b.setId(2L);b.setDidStatus(1);
        when(dids.selectList(any())).thenReturn(Arrays.asList(a,b));when(rules.selectList(any())).thenReturn(Collections.emptyList());
    }
    @Test void failedInspectionFreezesWholePartsBatchAndCreatesAlert() {
        quality.setInspectionResult("不合格");service.afterQuality(event,quality);assertEquals(2,a.getDidStatus());assertEquals(2,b.getDidStatus());verify(alerts).insert(any(AnomalyAlert.class));assertEquals(1,event.getAbnormalMark());
    }
    @Test void passedInspectionDoesNotFreeze() {quality.setInspectionResult("合格");service.afterQuality(event,quality);verify(dids,never()).updateById(any(PartsDid.class));verify(alerts,never()).insert(any(AnomalyAlert.class));}
    @Test void unresolvedAlertPreventsUnfreeze() {when(alerts.selectCount(any())).thenReturn(1L);assertThrows(BusinessException.class,()->service.assertCanUnfreeze(1L));}
    @Test void closingQualityAlertRequiresNewPassedInspection() {
        AnomalyAlert alert=new AnomalyAlert();alert.setPartsId(1L);alert.setUnitId(2L);alert.setEventId(1L);
        TraceEvent retest=new TraceEvent();retest.setId(2L);when(events.selectOne(any())).thenReturn(retest);when(qualities.selectOne(any())).thenReturn(quality);
        quality.setInspectionResult("不合格");assertThrows(BusinessException.class,()->service.assertReinspected(alert));quality.setInspectionResult("合格");assertDoesNotThrow(()->service.assertReinspected(alert));
    }
    @Test void sqlRulesAreRejectedAndSupportedJsonRulesAccepted() {
        AnomalyRule rule=new AnomalyRule();rule.setRuleType("quality");rule.setCondition("inspection_result='不合格'");rule.setAction("freeze");assertThrows(BusinessException.class,()->QualitySafetyService.validateRule(rule));
        rule.setCondition("{\"field\":\"inspectionResult\",\"operator\":\"eq\",\"value\":\"不合格\"}");rule.setAction("{\"type\":\"freeze_batch\"}");assertDoesNotThrow(()->QualitySafetyService.validateRule(rule));
    }
}
