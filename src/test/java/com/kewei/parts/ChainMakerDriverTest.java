package com.kewei.parts;

import com.kewei.parts.infrastructure.chain.ChainNetworkProperties;
import com.kewei.parts.infrastructure.chainmaker.ChainMakerSdkInvoker;
import com.kewei.parts.infrastructure.chainmaker.ChainMakerKeyMaterial;
import com.kewei.parts.infrastructure.storage.ChainRouter;
import com.google.protobuf.ByteString;
import org.chainmaker.pb.common.ResultOuterClass.*;
import org.chainmaker.sdk.ChainClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.ECGenParameterSpec;
import java.io.StringWriter;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.chainmaker.sdk.crypto.ChainmakerX509CryptoSuite;
import org.chainmaker.sdk.utils.CryptoUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChainMakerDriverTest {
    private ChainMakerSdkInvoker driver;
    private ChainClient client;
    private TxResponse response;
    @BeforeEach void prepare() throws Exception {
        client=mock(ChainClient.class);driver=new ChainMakerSdkInvoker(new DefaultResourceLoader(),new ChainNetworkProperties());
        Map<String,ChainClient> connections=new HashMap<>();connections.put(ChainRouter.SUB_PRODUCTION,client);
        ReflectionTestUtils.setField(driver,"clients",connections);
        response=TxResponse.newBuilder().setCode(TxStatusCode.SUCCESS).setTxId("confirmed-cm-tx").setTxBlockHeight(9)
                .setContractResult(ContractResult.newBuilder().setCode(0).setResult(ByteString.copyFromUtf8("actual-result"))).build();
    }
    @Test void confirmedSdkReceiptAndContractResultAreUsed() throws Exception {
        when(client.invokeContract(anyString(),anyString(),anyString(),anyMap(),anyLong(),anyLong())).thenReturn(response);
        assertEquals("confirmed-cm-tx",driver.invoke(ChainRouter.SUB_PRODUCTION,"save_anchor",Collections.emptyMap()).getTxId());
        when(client.queryContract(anyString(),anyString(),anyString(),anyMap(),anyLong())).thenReturn(response);
        assertEquals("actual-result",driver.query(ChainRouter.SUB_PRODUCTION,"get_anchor",Collections.emptyMap()));
    }
    @Test void failedOrMissingContractReceiptIsRejected() {
        assertThrows(Exception.class,()->ChainMakerSdkInvoker.check(null));
        assertThrows(Exception.class,()->ChainMakerSdkInvoker.check(response.toBuilder().clearContractResult().build()));
        assertThrows(Exception.class,()->ChainMakerSdkInvoker.check(response.toBuilder().setContractResult(ContractResult.newBuilder().setCode(1)).build()));
        assertThrows(Exception.class,()->ChainMakerSdkInvoker.check(response.toBuilder().setCodeValue(1).build()));
    }
    @Test void unconfirmedReceiptCannotBecomeSuccessfulAnchor() throws Exception {
        when(client.invokeContract(anyString(),anyString(),anyString(),anyMap(),anyLong(),anyLong())).thenReturn(response.toBuilder().setTxBlockHeight(0).build());
        assertThrows(Exception.class,()->driver.invoke(ChainRouter.SUB_PRODUCTION,"save_anchor",Collections.emptyMap()));
    }
    @Test void otherLogicalChainCannotReuseExistingClient() {
        assertThrows(IllegalStateException.class,()->driver.query(ChainRouter.SUB_LOGISTICS,"get_anchor",Collections.emptyMap()));
        driver.close();verify(client).stop();assertFalse(driver.configured(ChainRouter.SUB_PRODUCTION));
    }
    @Test void chainMakerSdkStillSignsAndReadsKeysWithSharedCryptoDependencies() throws Exception {
        ChainmakerX509CryptoSuite suite=ChainmakerX509CryptoSuite.newInstance(false);
        byte[] message="mixed-platform-sdk".getBytes(StandardCharsets.UTF_8);
        for(String algorithm:new String[]{"SHA256withECDSA","SM3withSM2"}) {
            KeyPair keys;
            if(algorithm.equals("SM3withSM2")) {
                KeyPairGenerator generator=KeyPairGenerator.getInstance("EC","BC");
                generator.initialize(new ECGenParameterSpec("sm2p256v1"));keys=generator.generateKeyPair();
            } else keys=suite.keyGen();
            suite.setAlgorithm(algorithm);
            StringWriter text=new StringWriter();try(JcaPEMWriter pem=new JcaPEMWriter(text)){pem.writeObject(keys.getPrivate());}
            byte[] original=text.toString().getBytes(StandardCharsets.UTF_8);
            org.chainmaker.sdk.config.ChainClientConfig config=new org.chainmaker.sdk.config.ChainClientConfig();
            config.setUserKeyBytes(original);config.setUserSignKeyBytes(original);ChainMakerKeyMaterial.prepare(config);
            PrivateKey restored=CryptoUtils.getPrivateKeyFromBytes(config.getUserSignKeyBytes());
            assertArrayEquals(restored.getEncoded(),CryptoUtils.getPrivateKeyFromBytes(config.getUserKeyBytes()).getEncoded());
            byte[] signed=suite.sign(restored,message);
            Signature verifier=Signature.getInstance(algorithm,"BC");verifier.initVerify(keys.getPublic());verifier.update(message);
            assertTrue(verifier.verify(signed));
        }
    }
    @Test void keyLoaderRejectsPublicKeysAndMultiplePrivateKeys() throws Exception {
        KeyPair pair=KeyPairGenerator.getInstance("RSA").generateKeyPair();
        StringWriter publicText=new StringWriter();try(JcaPEMWriter pem=new JcaPEMWriter(publicText)){pem.writeObject(pair.getPublic());}
        assertThrows(IllegalArgumentException.class,()->ChainMakerKeyMaterial.normalize(publicText.toString().getBytes(StandardCharsets.UTF_8)));
        StringWriter privateText=new StringWriter();try(JcaPEMWriter pem=new JcaPEMWriter(privateText)){pem.writeObject(pair.getPrivate());pem.writeObject(pair.getPrivate());}
        assertThrows(IllegalArgumentException.class,()->ChainMakerKeyMaterial.normalize(privateText.toString().getBytes(StandardCharsets.UTF_8)));
    }
}
