package com.kewei.parts;

import com.kewei.parts.infrastructure.chain.*;
import com.kewei.parts.infrastructure.storage.ChainRouter;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ChainConfigurationTest {
    private ChainNetworkProperties real() {
        ChainNetworkProperties p = new ChainNetworkProperties();
        p.setStorageMode("real"); p.setRelayPrivateKey("external-key");
        p.getNetworks().values().forEach(n -> {
            n.setSdkConfig("file:/external/sdk-config");
            n.setContractAddress("0x1111111111111111111111111111111111111111");
            n.setExpectedAccount("0x2222222222222222222222222222222222222222");
        });
        return p;
    }
    @Test void realAndLegacyModesAcceptMixedPlatformConfiguration() {
        ChainNetworkProperties p=real(); assertDoesNotThrow(p::validate);
        p.setStorageMode("chainmaker"); assertDoesNotThrow(p::validate);
    }
    @Test void missingSdkNeverFallsBackToAnotherNetwork() {
        ChainNetworkProperties p=real();p.network(ChainRouter.SUB_LOGISTICS).setSdkConfig("");
        assertThrows(IllegalArgumentException.class,p::validate);
    }
    @Test void invalidOrZeroFiscoAddressIsRejected() {
        ChainNetworkProperties p=real();
        for(String value:new String[]{null,"trace_anchor","0x0000000000000000000000000000000000000000"}) {
            p.network(ChainRouter.SUB_LOGISTICS).setContractAddress(value);
            assertThrows(IllegalArgumentException.class,p::validate);
        }
    }
    @Test void fiscoRequiresFixedAccountAndGroup() {
        ChainNetworkProperties p=real();p.network(ChainRouter.SUB_LOGISTICS).setExpectedAccount(null);
        assertThrows(IllegalArgumentException.class,p::validate);
        p=real();p.network(ChainRouter.SUB_LOGISTICS).setGroup("");
        assertThrows(IllegalArgumentException.class,p::validate);
    }
    @Test void unsupportedModeAndMissingRelayKeyFailClearly() {
        ChainNetworkProperties p=real();p.setStorageMode("fisco");assertThrows(IllegalArgumentException.class,p::validate);
        p.setStorageMode("real");p.setRelayPrivateKey(null);assertThrows(IllegalArgumentException.class,p::validate);
    }
    @Test void databaseModeDoesNotRequireNativeSdkSecrets() {
        assertDoesNotThrow(new ChainNetworkProperties()::validate);
    }
    @Test void chainMakerTemplateUsesPropertiesUnderstoodByTheActualSdk() throws Exception {
        try(java.io.InputStream stream=java.nio.file.Files.newInputStream(java.nio.file.Paths.get("src/main/resources/chainmaker/sdk_config.yml.example"))) {
            org.chainmaker.sdk.config.SdkConfig sdk=new org.yaml.snakeyaml.Yaml().loadAs(stream,org.chainmaker.sdk.config.SdkConfig.class);
            org.chainmaker.sdk.config.ChainClientConfig config=sdk.getChain_client();
            assertNotNull(config);assertTrue(config.getUserSignKeyFilePath().endsWith("user.sign.key"));
            assertEquals("chainmaker.org",config.getNodes()[0].getTlsHostName());
            assertEquals(1,config.getNodes()[0].getTrustRootPaths().length);
        }
    }
}
