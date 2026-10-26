package com.kewei.parts;

import com.kewei.parts.infrastructure.chain.*;
import com.kewei.parts.infrastructure.chainmaker.*;
import com.kewei.parts.infrastructure.fisco.*;
import com.kewei.parts.infrastructure.storage.*;
import com.kewei.parts.utils.RsaUtil;
import org.junit.jupiter.api.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ChainPlatformRoutingTest {
    private static final String HASH_A = String.join("", Collections.nCopies(64, "a"));
    private static final String HASH_B = String.join("", Collections.nCopies(64, "b"));
    static Map<String,String> keys;
    ChainNetworkProperties properties;
    ChainDriver chainmaker, fisco;
    ChainSdkRouter router;
    @BeforeAll static void keyPair() throws Exception {keys=RsaUtil.generateKeyPair();}
    @BeforeEach void setup() throws Exception {
        properties=new ChainNetworkProperties();properties.setStorageMode("real");properties.setRelayPrivateKey(keys.get("privateKey"));
        chainmaker=mock(ChainDriver.class);fisco=mock(ChainDriver.class);
        when(chainmaker.platform()).thenReturn(ChainPlatform.CHAINMAKER);when(fisco.platform()).thenReturn(ChainPlatform.FISCO_BCOS);
        when(chainmaker.configured(anyString())).thenReturn(true);when(fisco.configured(anyString())).thenReturn(true);
        when(chainmaker.physicalIdentity(anyString())).thenAnswer(i->"CHAINMAKER:"+i.getArgument(0));
        when(fisco.physicalIdentity(anyString())).thenAnswer(i->"FISCO_BCOS:"+i.getArgument(0)+":group0");
        when(chainmaker.query(anyString(),eq("contract_info"),anyMap())).thenAnswer(i->info(i.getArgument(0)));
        when(fisco.query(anyString(),eq("contract_info"),anyMap())).thenAnswer(i->info(i.getArgument(0)));
        when(chainmaker.invoke(anyString(),anyString(),anyMap())).thenReturn(new ChainStorageAdapter.StoreChainResult("cm-tx",1L));
        when(fisco.invoke(anyString(),anyString(),anyMap())).thenReturn(new ChainStorageAdapter.StoreChainResult("fisco-tx",2L));
        router=new ChainSdkRouter(properties,Arrays.asList(chainmaker,fisco));
    }
    private String info(String code) {return "{\"schema\":\"parts-trace-anchor/v2\",\"chainCode\":\""+code+"\"}";}
    private ChainAnchorRequest request(String code) {return ChainAnchorRequest.builder().chainId(code).businessType("trace_event").businessId(1L).dataHash(HASH_A).objectCid("cid").partsDid("did:test:unit").build();}
    private CrossChainAnchorPayload payload(String source) {return CrossChainAnchorPayload.builder().sourceChainId(source).sourceTxHash("real-source-tx").sourceBlockHeight(10L).businessType("trace_event").businessId(1L).dataHash(HASH_A).objectCid("cid").partsDid("did:test:unit").build();}
    private String anchor() {return "{\"data_hash\":\""+HASH_A+"\",\"object_cid\":\"cid\",\"parts_did\":\"did:test:unit\"}";}

    @Test void logisticsInvokesFiscoAndProductionInvokesChainMaker() throws Exception {
        assertEquals("fisco-tx",router.storeAnchor(request(ChainRouter.SUB_LOGISTICS)).getTxId());
        assertEquals("cm-tx",router.storeAnchor(request(ChainRouter.SUB_PRODUCTION)).getTxId());
        verify(fisco).invoke(eq(ChainRouter.SUB_LOGISTICS),eq("save_anchor"),anyMap());
        verify(chainmaker,never()).invoke(eq(ChainRouter.SUB_LOGISTICS),anyString(),anyMap());
    }
    @Test void anchorReadsRouteByPlatform() throws Exception {
        when(fisco.query(eq(ChainRouter.SUB_LOGISTICS),eq("get_anchor"),anyMap())).thenReturn(anchor());
        assertEquals(HASH_A,router.getAnchorHash(ChainRouter.SUB_LOGISTICS,"trace_event",1L));
        verify(chainmaker,never()).query(eq(ChainRouter.SUB_LOGISTICS),anyString(),anyMap());
    }
    @Test void fiscoSourceRelaysToChainMakerTargetWithRsaSignature() throws Exception {
        when(fisco.query(eq(ChainRouter.SUB_LOGISTICS),eq("get_anchor"),anyMap())).thenReturn(anchor());
        assertEquals("cm-tx",router.relay(payload(ChainRouter.SUB_LOGISTICS)).getTxId());
        verify(chainmaker).invoke(eq(ChainRouter.MAIN_REGULATOR),eq("receive_cross_anchor"),argThat(args->args.containsKey("signature") && ContractArguments.text(args,"source_chain_id").equals(ChainRouter.SUB_LOGISTICS)));
    }
    @Test void chainMakerSourceCanRelayToFiscoTarget() throws Exception {
        properties.network(ChainRouter.MAIN_REGULATOR).setPlatform(ChainPlatform.FISCO_BCOS);
        when(chainmaker.query(eq(ChainRouter.SUB_PRODUCTION),eq("get_anchor"),anyMap())).thenReturn(anchor());
        assertEquals("fisco-tx",router.relay(payload(ChainRouter.SUB_PRODUCTION)).getTxId());
        verify(fisco).invoke(eq(ChainRouter.MAIN_REGULATOR),eq("receive_cross_anchor"),anyMap());
    }
    @Test void sourceHashSubjectAndCidMustAllMatch() throws Exception {
        for(String source:Arrays.asList(anchor().replace("\"cid\"","\"wrong-cid\""),anchor().replace("did:test:unit","did:test:other"),anchor().replace(HASH_A,HASH_B))) {
            when(fisco.query(eq(ChainRouter.SUB_LOGISTICS),eq("get_anchor"),anyMap())).thenReturn(source);
            assertThrows(IllegalStateException.class,()->router.relay(payload(ChainRouter.SUB_LOGISTICS)));
        }
        verify(chainmaker,never()).invoke(anyString(),eq("receive_cross_anchor"),anyMap());
    }
    @Test void fiscoTargetSupportsVcAppendReadAndRevocation() throws Exception {
        properties.network(ChainRouter.MAIN_REGULATOR).setPlatform(ChainPlatform.FISCO_BCOS);
        router.appendVc("did:test:unit","VC-1",HASH_A);router.revokeVc("did:test:unit","VC-1");
        when(fisco.query(eq(ChainRouter.MAIN_REGULATOR),eq("get_vc"),anyMap())).thenReturn("0");assertEquals("0",router.getVcHash("did:test:unit","VC-1"));
        verify(fisco).invoke(eq(ChainRouter.MAIN_REGULATOR),eq("append_vc"),anyMap());verify(fisco).invoke(eq(ChainRouter.MAIN_REGULATOR),eq("revoke_vc"),anyMap());
    }
    @Test void missingDriverCannotFallBackToAnotherPlatform() {router=new ChainSdkRouter(properties,Collections.singletonList(chainmaker));assertThrows(IllegalStateException.class,()->router.storeAnchor(request(ChainRouter.SUB_LOGISTICS)));}
    @Test void unknownLogicalChainRejected() {assertThrows(IllegalArgumentException.class,()->router.storeAnchor(request("unknown-chain")));}
    @Test void samePhysicalLedgerCannotMasqueradeAsMultipleChains() throws Exception {when(chainmaker.physicalIdentity(anyString())).thenReturn("CHAINMAKER:same-chain");assertThrows(IllegalStateException.class,router::verifyBindings);}
    @Test void wrongContractVersionOrChainCodeRejected() throws Exception {when(fisco.query(eq(ChainRouter.SUB_LOGISTICS),eq("contract_info"),anyMap())).thenReturn(info("wrong-chain"));assertThrows(IllegalStateException.class,router::verifyBindings);}
    @Test void validDistinctNetworkBindingsPass() {assertDoesNotThrow(router::verifyBindings);}
    @Test void databaseModeDoesNotInitializeEitherNativeSdk() {
        new ApplicationContextRunner().withUserConfiguration(DatabaseMode.class).withPropertyValues("parts.trace.storage.chain-mode=db").run(context->{
            assertNull(context.getStartupFailure());assertEquals(0,context.getBeansOfType(ChainDriver.class).size());assertNotNull(context.getBean(ChainSdkRouter.class));
        });
    }
    @Configuration
    @EnableConfigurationProperties(ChainNetworkProperties.class)
    @Import({ChainSdkRouter.class,ChainMakerSdkInvoker.class,FiscoBcosDriver.class,FiscoConnectionFactory.class})
    static class DatabaseMode {}
}
