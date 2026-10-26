package com.kewei.parts.infrastructure.chain;

public final class ChainModes {
    public static final String REAL = "'${parts.trace.storage.chain-mode:db}' == 'real' || '${parts.trace.storage.chain-mode:db}' == 'chainmaker'";

    private ChainModes() {}

    public static boolean isReal(String mode) {
        return "real".equals(mode) || "chainmaker".equals(mode);
    }
}
