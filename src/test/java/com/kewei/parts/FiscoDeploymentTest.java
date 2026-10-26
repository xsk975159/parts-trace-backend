package com.kewei.parts;

import com.alibaba.fastjson.JSON;
import com.kewei.parts.infrastructure.fisco.FiscoContractDeployer;
import org.fisco.bcos.sdk.v3.model.TransactionReceipt;
import org.fisco.bcos.sdk.v3.transaction.model.dto.TransactionResponse;
import org.junit.jupiter.api.Test;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.KeyPairGenerator;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FiscoDeploymentTest {
    private FiscoContractDeployer.Manifest manifest() {
        FiscoContractDeployer.Manifest m=new FiscoContractDeployer.Manifest();m.setSdkConfig("file:/external/sdk_config.toml");
        m.setExpectedChainId("chain0");m.setExpectedAccount("0x1111111111111111111111111111111111111111");m.setLocalChainCode("main-regulator");return m;
    }
    @Test void relayPublicKeyIsConvertedToUnsignedRsaConstructorFields() throws Exception {
        FiscoContractDeployer.Manifest m=manifest();
        String key=JSON.parseObject(new String(Files.readAllBytes(Paths.get("contracts/trace_anchor/testdata/relay-vector.json")),StandardCharsets.UTF_8)).getString("publicKey");
        m.getRelayPublicKeys().put("sub-production",key);
        List<Object> args=FiscoContractDeployer.constructorArguments(m);
        assertEquals(Collections.singletonList("sub-production"),args.get(2));
        assertEquals(256,((List<byte[]>)args.get(3)).get(0).length);
        assertArrayEquals(new byte[]{1,0,1},((List<byte[]>)args.get(4)).get(0));
    }
    @Test void deploymentRequiresFixedAccountAndPhysicalIdentity() {
        FiscoContractDeployer.Manifest m=manifest();m.setExpectedAccount(null);assertThrows(IllegalArgumentException.class,()->FiscoContractDeployer.constructorArguments(m));
        m.setExpectedAccount("0x1111111111111111111111111111111111111111");m.setExpectedChainId(null);assertThrows(IllegalArgumentException.class,()->FiscoContractDeployer.constructorArguments(m));
    }
    @Test void weakRsaKeyAndDuplicateWritersAreRejectedBeforeConnecting() throws Exception {
        FiscoContractDeployer.Manifest m=manifest();KeyPairGenerator g=KeyPairGenerator.getInstance("RSA");g.initialize(1024);
        m.getRelayPublicKeys().put("sub-production",Base64.getEncoder().encodeToString(g.generateKeyPair().getPublic().getEncoded()));
        assertThrows(IllegalArgumentException.class,()->FiscoContractDeployer.constructorArguments(m));
        m.getRelayPublicKeys().clear();m.setWriterAddresses(Arrays.asList(m.getExpectedAccount(),m.getExpectedAccount()));
        assertThrows(IllegalArgumentException.class,()->FiscoContractDeployer.constructorArguments(m));
    }
    @Test void failedOrUnconfirmedDeploymentDoesNotProduceAddress() {
        TransactionReceipt receipt=new TransactionReceipt();receipt.setStatus(0);receipt.setBlockNumber(BigInteger.ONE);
        receipt.setFrom(manifest().getExpectedAccount());receipt.setTransactionHash("0x"+String.join("",Collections.nCopies(64,"a")));
        TransactionResponse response=new TransactionResponse();response.setReturnCode(0);response.setTransactionReceipt(receipt);
        response.setContractAddress("0x2222222222222222222222222222222222222222");
        assertDoesNotThrow(()->FiscoContractDeployer.validateDeployment(response,manifest().getExpectedAccount()));
        receipt.setBlockNumber(BigInteger.ZERO);assertThrows(IllegalStateException.class,()->FiscoContractDeployer.validateDeployment(response,manifest().getExpectedAccount()));
        assertThrows(IllegalStateException.class,()->FiscoContractDeployer.validateDeployment(null,manifest().getExpectedAccount()));
    }
}
