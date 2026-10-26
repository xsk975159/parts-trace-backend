package com.kewei.parts;
import com.kewei.parts.infrastructure.chain.*;
import com.kewei.parts.infrastructure.fisco.*;
import com.kewei.parts.infrastructure.storage.*;
import org.fisco.bcos.sdk.v3.model.TransactionReceipt;
import org.fisco.bcos.sdk.v3.transaction.model.dto.*;
import org.junit.jupiter.api.*;
import java.math.BigInteger;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class FiscoDriverTest {
    private static final String DATA_HASH=String.join("",Collections.nCopies(64,"a"));
    static final String ADDRESS="0x"+String.join("",Collections.nCopies(20,"11")),
            ACCOUNT="0x"+String.join("",Collections.nCopies(20,"22")),
            HASH="0x"+String.join("",Collections.nCopies(32,"ab"));
    FiscoBcosDriver driver;FiscoConnection connection;TransactionResponse response;TransactionReceipt receipt;
    @BeforeEach void setup() throws Exception {
        ChainNetworkProperties properties=new ChainNetworkProperties();properties.setStorageMode("real");
        FiscoConnectionFactory factory=mock(FiscoConnectionFactory.class);connection=mock(FiscoConnection.class);when(factory.connect(any())).thenReturn(connection);
        when(connection.address()).thenReturn(ADDRESS);when(connection.account()).thenReturn(ACCOUNT);when(connection.physicalIdentity()).thenReturn("FISCO_BCOS:chain0:group0");
        receipt=new TransactionReceipt();receipt.setStatus(0);receipt.setTransactionHash(HASH);receipt.setBlockNumber(BigInteger.valueOf(123));receipt.setFrom(ACCOUNT);receipt.setTo(ADDRESS);
        response=new TransactionResponse();response.setReturnCode(0);response.setTransactionReceipt(receipt);when(connection.invoke(anyString(),anyList())).thenReturn(response);
        driver=new FiscoBcosDriver(properties,factory);driver.initialize();
    }
    Map<String,byte[]> arguments() {return ContractArguments.anchor(ChainAnchorRequest.builder().businessType("trace_event").businessId(1L).dataHash(DATA_HASH).objectCid("cid").partsDid("did:test:unit").build());}
    @Test void nativeReceiptConvertedWithoutSyntheticTransaction() throws Exception {
        ChainStorageAdapter.StoreChainResult result=driver.invoke(ChainRouter.SUB_LOGISTICS,"save_anchor",arguments());assertEquals(HASH,result.getTxId());assertEquals(123L,result.getBlockHeight());assertFalse(result.isSimulated());
        verify(connection).invoke("save_anchor",Arrays.asList("trace_event","1",DATA_HASH,"cid","did:test:unit"));
    }
    @Test void revertReceiptRejected() {receipt.setStatus(16);assertThrows(IllegalStateException.class,()->driver.invoke(ChainRouter.SUB_LOGISTICS,"save_anchor",arguments()));}
    @Test void outerSdkFailureRejected() {response.setReturnCode(-1);assertThrows(IllegalStateException.class,()->driver.invoke(ChainRouter.SUB_LOGISTICS,"save_anchor",arguments()));}
    @Test void missingReceiptRejected() {response.setTransactionReceipt(null);assertThrows(IllegalStateException.class,()->driver.invoke(ChainRouter.SUB_LOGISTICS,"save_anchor",arguments()));}
    @Test void unconfirmedOrMissingTransactionRejected() {receipt.setBlockNumber(BigInteger.ZERO);assertThrows(IllegalStateException.class,()->driver.invoke(ChainRouter.SUB_LOGISTICS,"save_anchor",arguments()));receipt.setBlockNumber(BigInteger.ONE);receipt.setTransactionHash(null);assertThrows(IllegalStateException.class,()->driver.invoke(ChainRouter.SUB_LOGISTICS,"save_anchor",arguments()));}
    @Test void receiptForDifferentContractRejected() {receipt.setTo(ACCOUNT);assertThrows(IllegalStateException.class,()->driver.invoke(ChainRouter.SUB_LOGISTICS,"save_anchor",arguments()));}
    @Test void receiptForDifferentSenderRejected() {receipt.setFrom(ADDRESS);assertThrows(IllegalStateException.class,()->driver.invoke(ChainRouter.SUB_LOGISTICS,"save_anchor",arguments()));}
    @Test void anchorTupleNormalizedToSharedSchema() throws Exception {
        CallResponse call=new CallResponse();call.setReturnCode(0);call.setReturnObject(Arrays.asList(DATA_HASH,"cid","did:test:unit"));when(connection.query(anyString(),anyList())).thenReturn(call);
        String raw=driver.query(ChainRouter.SUB_LOGISTICS,"get_anchor",ContractArguments.anchorKey("trace_event",1L));assertEquals(DATA_HASH,com.alibaba.fastjson.JSON.parseObject(raw).getString("data_hash"));
    }
    @Test void failedOrMalformedQueryDoesNotBecomeSuccessful() throws Exception {
        CallResponse call=new CallResponse();call.setReturnCode(-1);when(connection.query(anyString(),anyList())).thenReturn(call);assertThrows(IllegalStateException.class,()->driver.query(ChainRouter.SUB_LOGISTICS,"get_vc",ContractArguments.vc("did:test:unit","VC-1",null)));
        call.setReturnCode(0);call.setReturnObject(Collections.singletonList(12));assertThrows(IllegalStateException.class,()->driver.query(ChainRouter.SUB_LOGISTICS,"get_vc",ContractArguments.vc("did:test:unit","VC-1",null)));
    }
    @Test void crossChainSignatureConvertedToBytesAndFieldsInAbiOrder() {
        Map<String,byte[]> args=new HashMap<>();for(String key:FiscoBcosDriver.CROSS_FIELDS)ContractArguments.put(args,key,key);
        ContractArguments.put(args,"signature",Base64.getEncoder().encodeToString(new byte[]{1,2,3}));List<Object> parameters=FiscoBcosDriver.parameters("receive_cross_anchor",args);
        assertEquals(FiscoBcosDriver.CROSS_FIELDS,parameters.get(0));assertArrayEquals(new byte[]{1,2,3},(byte[])parameters.get(1));
    }
    @Test void onlyFiscoNetworksAreRegisteredAndClientsClose() {assertTrue(driver.configured(ChainRouter.SUB_LOGISTICS));assertFalse(driver.configured(ChainRouter.SUB_PRODUCTION));driver.close();verify(connection).close();assertFalse(driver.configured(ChainRouter.SUB_LOGISTICS));}
}
