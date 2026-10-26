package com.kewei.parts.module.parts.service;
import com.alibaba.fastjson.JSON;
import com.kewei.parts.infrastructure.storage.*;
import com.kewei.parts.module.parts.entity.PartsDid;
import com.kewei.parts.utils.DidUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
@Component
public class DidDocumentVerifier {
    @Autowired private ObjectStorageAdapter objects;
    @Autowired private ChainStorageAdapter chains;
    @Autowired private ChainRouter router;
    @Autowired private DidUtil hashes;
    public boolean verify(PartsDid did) {
        try {
            return did!=null && did.getUnitId()!=null && did.getDidDocument()!=null
                && did.getDid().equals(JSON.parseObject(did.getDidDocument()).getString("id"))
                && hashes.sha256Hex(did.getDidDocument()).equals(did.getDocHash())
                && did.getDocHash().equals(chains.getAnchorHash(router.didChain(),"did_document_unit",did.getUnitId()))
                && did.getDidDocument().equals(objects.read(did.getDocCid()));
        } catch(Exception e) {return false;}
    }
}
