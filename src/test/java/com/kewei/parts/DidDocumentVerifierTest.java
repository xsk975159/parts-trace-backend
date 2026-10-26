package com.kewei.parts;
import com.kewei.parts.module.parts.entity.PartsDid;
import com.kewei.parts.module.parts.service.DidDocumentVerifier;
import com.kewei.parts.infrastructure.storage.*;
import com.kewei.parts.utils.DidUtil;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class DidDocumentVerifierTest {
    DidDocumentVerifier verifier;PartsDid did;ObjectStorageAdapter objects;ChainStorageAdapter chains;
    @BeforeEach void setup() {
        verifier=new DidDocumentVerifier();objects=mock(ObjectStorageAdapter.class);chains=mock(ChainStorageAdapter.class);
        ReflectionTestUtils.setField(verifier,"objects",objects);ReflectionTestUtils.setField(verifier,"chains",chains);ReflectionTestUtils.setField(verifier,"router",new ChainRouter());ReflectionTestUtils.setField(verifier,"hashes",new DidUtil());
        did=new PartsDid();did.setUnitId(1L);did.setDid("did:test:unit");did.setDidDocument("{\"id\":\"did:test:unit\"}");did.setDocHash(new DidUtil().sha256Hex(did.getDidDocument()));did.setDocCid("document-cid");
        when(objects.read("document-cid")).thenReturn(did.getDidDocument());when(chains.getAnchorHash(ChainRouter.SUB_PRODUCTION,"did_document_unit",1L)).thenReturn(did.getDocHash());
    }
    @Test void matchingDocumentAndAnchorPass() {assertTrue(verifier.verify(did));}
    @Test void replacedDidFails() {did.setDid("did:test:another");assertFalse(verifier.verify(did));}
    @Test void changedDocumentFails() {did.setDidDocument("{\"id\":\"did:test:unit\",\"modified\":true}");assertFalse(verifier.verify(did));}
    @Test void unavailableAnchorFailsClosed() {when(chains.getAnchorHash(ChainRouter.SUB_PRODUCTION,"did_document_unit",1L)).thenThrow(new IllegalStateException("offline"));assertFalse(verifier.verify(did));}
}
