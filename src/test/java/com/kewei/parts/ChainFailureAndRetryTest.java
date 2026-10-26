package com.kewei.parts;
import com.kewei.parts.infrastructure.chainmaker.*;
import com.kewei.parts.infrastructure.chain.ChainSdkRouter;
import com.kewei.parts.infrastructure.storage.*;
import com.kewei.parts.infrastructure.storage.impl.MultiChainStorageAdapter;
import com.kewei.parts.module.parts.entity.*;
import com.kewei.parts.module.parts.mapper.*;
import com.kewei.parts.module.trace.entity.TraceEvent;
import com.kewei.parts.module.trace.mapper.*;
import com.kewei.parts.utils.DidUtil;
import org.chainmaker.sdk.ChainClientException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ChainFailureAndRetryTest {
    private ChainAnchorRequest request() {return ChainAnchorRequest.builder().chainId(ChainRouter.SUB_PRODUCTION).businessType("trace_event").businessId(1L).dataHash("hash").partsDid("did:test:unit").build();}
    @Test void realChainFailureNeverCreatesFakeReceipt() throws Exception {
        MultiChainStorageAdapter adapter=new MultiChainStorageAdapter();ChainSdkRouter sdk=mock(ChainSdkRouter.class);BlockchainRecordMapper records=mock(BlockchainRecordMapper.class);
        ReflectionTestUtils.setField(adapter,"sdkRouter",sdk);ReflectionTestUtils.setField(adapter,"blockchainRecordMapper",records);
        when(sdk.storeAnchor(any())).thenThrow(new ChainClientException("offline"));
        assertThrows(IllegalStateException.class,()->adapter.storeAnchor(request()));verify(records,never()).insert(any(BlockchainRecord.class));
    }
    @Test void successfulRealChainStoreEnqueuesCrossChainRelay() throws Exception {
        MultiChainStorageAdapter adapter=new MultiChainStorageAdapter();ChainSdkRouter sdk=mock(ChainSdkRouter.class);CrossChainRelayService relay=mock(CrossChainRelayService.class);
        ReflectionTestUtils.setField(adapter,"sdkRouter",sdk);ReflectionTestUtils.setField(adapter,"relay",relay);ReflectionTestUtils.setField(adapter,"router",new ChainRouter());ReflectionTestUtils.setField(adapter,"blockchainRecordMapper",mock(BlockchainRecordMapper.class));
        when(sdk.storeAnchor(any())).thenReturn(new ChainStorageAdapter.StoreChainResult("real-source-tx",123L));
        assertEquals("real-source-tx",adapter.storeAnchor(request()).getTxId());verify(relay).relayToMasterChain(argThat(p->"real-source-tx".equals(p.getSourceTxHash()) && p.getBusinessId().equals(1L)));
    }
    @Test void durableTaskReturnsPendingAndRetriesFailureWithoutInventingTransaction() {
        DurableChainStorageAdapter adapter=new DurableChainStorageAdapter();ChainTaskMapper tasks=mock(ChainTaskMapper.class);ChainStorageAdapter delegate=mock(ChainStorageAdapter.class);TraceEventMapper events=mock(TraceEventMapper.class);
        ReflectionTestUtils.setField(adapter,"tasks",tasks);ReflectionTestUtils.setField(adapter,"delegate",delegate);ReflectionTestUtils.setField(adapter,"events",events);ReflectionTestUtils.setField(adapter,"hashes",new DidUtil());
        AtomicReference<ChainTask> saved=new AtomicReference<>();doAnswer(i->{ChainTask task=i.getArgument(0);task.setId(1L);saved.set(task);return 1;}).when(tasks).insert(any(ChainTask.class));
        ChainStorageAdapter.StoreChainResult pending=adapter.storeAnchor(request());assertNull(pending.getTxId());verify(delegate,never()).storeAnchor(any());
        when(tasks.selectList(any())).thenAnswer(i->Collections.singletonList(saved.get()));when(tasks.claim(1L)).thenReturn(1);when(delegate.storeAnchor(any())).thenThrow(new IllegalStateException("offline"));
        adapter.processPending();assertEquals(-1,saved.get().getStatus());assertNull(saved.get().getTxId());assertEquals(1,saved.get().getAttempts());verify(events).updateById(argThat(e->e.getChainStatus()==-1));
        doReturn(new ChainStorageAdapter.StoreChainResult("real-tx-after-retry",42L)).when(delegate).storeAnchor(any());adapter.processPending();assertEquals(1,saved.get().getStatus());assertEquals("real-tx-after-retry",saved.get().getTxId());
    }
    @Test void simulatedRelayIsLabeledAndNeverCallsRealSdk() throws Exception {
        CrossChainRelayService relay=new CrossChainRelayService();CrossChainRecordMapper mapper=mock(CrossChainRecordMapper.class);ChainSdkRouter sdk=mock(ChainSdkRouter.class);
        ReflectionTestUtils.setField(relay,"mapper",mapper);ReflectionTestUtils.setField(relay,"mode","db");ReflectionTestUtils.setField(relay,"sdkRouter",sdk);when(mapper.selectMaxTargetBlockHeight()).thenReturn(0L);
        relay.relayToMasterChain(CrossChainAnchorPayload.builder().sourceChainId("sub-production").sourceTxHash("sim-source").sourceBlockHeight(1L).businessType("vc").businessId(3L).partsDid("did:test:unit").dataHash("hash").build());
        verify(mapper).insert(argThat(r->r.getStatus()==2 && r.getTargetTxHash().startsWith("sim-main-")));verify(sdk,never()).relay(any());
    }
}
