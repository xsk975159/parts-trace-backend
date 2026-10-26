package com.kewei.parts;
import com.kewei.parts.common.BusinessException;
import com.kewei.parts.module.parts.entity.*;
import com.kewei.parts.module.parts.mapper.*;
import com.kewei.parts.module.system.entity.SysUser;
import com.kewei.parts.module.trace.dto.ProductionEventDTO;
import com.kewei.parts.module.trace.entity.TraceEvent;
import com.kewei.parts.module.trace.mapper.TraceEventMapper;
import com.kewei.parts.module.trace.service.EventSnapshot;
import com.kewei.parts.module.trace.service.impl.TraceEventServiceImpl;
import com.kewei.parts.security.CurrentActor;
import com.kewei.parts.utils.*;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class EventIdempotencyTest {
    TraceEventServiceImpl service;TraceEventMapper events;ProductionEventDTO dto;
    @BeforeEach void setup() {
        service=new TraceEventServiceImpl();events=mock(TraceEventMapper.class);PartsInfoMapper parts=mock(PartsInfoMapper.class);PartsUnitMapper units=mock(PartsUnitMapper.class);CurrentActor actor=mock(CurrentActor.class);
        PartsInfo part=new PartsInfo();part.setId(1L);part.setCreatorId(7L);when(parts.lockById(1L)).thenReturn(part);
        PartsUnit unit=new PartsUnit();unit.setId(2L);unit.setPartsId(1L);when(units.selectById(2L)).thenReturn(unit);
        SysUser user=new SysUser();user.setId(7L);user.setUsername("real-actor");user.setOrganization("registered-org");when(actor.user()).thenReturn(user);when(actor.id()).thenReturn(7L);
        Object[][] deps={{"traceEventMapper",events},{"partsInfoMapper",parts},{"partsUnitMapper",units},{"actor",actor},{"snapshots",mock(EventSnapshot.class)},{"didUtil",new DidUtil()}};
        for(Object[] dep:deps)ReflectionTestUtils.setField(service,(String)dep[0],dep[1]);
        ReflectionTestUtils.setField(service,"validator",mock(javax.validation.Validator.class));
        dto=new ProductionEventDTO();dto.setPartsId(1L);dto.setUnitId(2L);dto.setRequestId("retry-key");dto.setEventTime(new Date(1000000));dto.setOperatorId(999L);dto.setOperatorName("forged");
    }
    @Test void identicalRetryReturnsExistingEventAndUsesAuthenticatedIdentity() {
        TraceEvent existing=new TraceEvent();when(events.selectOne(any())).thenAnswer(i->{existing.setRequestHash(new DidUtil().sha256Hex(CanonicalJson.write(dto)));return existing;});
        assertSame(existing,service.recordProductionEvent(dto));assertEquals(7L,dto.getOperatorId());assertEquals("real-actor",dto.getOperatorName());assertEquals("registered-org",dto.getOrganization());verify(events,never()).insert(any(TraceEvent.class));
    }
    @Test void reusedKeyWithDifferentPayloadRejected() {TraceEvent old=new TraceEvent();old.setRequestHash("different");when(events.selectOne(any())).thenReturn(old);assertThrows(BusinessException.class,()->service.recordProductionEvent(dto));}
    @Test void mismatchedUnitCannotBeAttachedToParts() {dto.setPartsId(8L);assertThrows(BusinessException.class,()->service.recordProductionEvent(dto));}
}
