package com.kewei.parts.infrastructure.desktop;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class HeterogeneousBootstrapTest {
    @TempDir Path temporary;

    @Test void refusesToResubmitAnUncertainDeployment() throws Exception {
        Path pending = temporary.resolve("deployment.pending");
        HeterogeneousBootstrap.beginDeployment(pending, "sub-logistics");
        byte[] original = Files.readAllBytes(pending);
        assertThrows(IllegalStateException.class, () -> HeterogeneousBootstrap.beginDeployment(pending, "sub-logistics"));
        assertArrayEquals(original, Files.readAllBytes(pending));
    }

    @Test void rejectsAContractBoundToAnotherLogicalChain() {
        assertThrows(IllegalStateException.class, () -> HeterogeneousBootstrap.checkInfo(
                "{\"schema\":\"parts-trace-anchor/v2\",\"chainCode\":\"sub-quality\"}", "sub-production"));
    }

    @Test void rejectsAnOlderContractSchema() {
        assertThrows(IllegalStateException.class, () -> HeterogeneousBootstrap.checkInfo(
                "{\"schema\":\"parts-trace-anchor/v1\",\"chainCode\":\"main-regulator\"}", "main-regulator"));
    }
}
