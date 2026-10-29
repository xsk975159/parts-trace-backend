package com.kewei.parts.infrastructure.confirmation;

import com.kewei.parts.infrastructure.chain.ChainSdkRouter;
import com.kewei.parts.utils.RsaUtil;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import java.util.Collections;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** 此接口不使用用户JWT，使用配置的协调端公钥核实整个请求。 */
@RestController
@ConditionalOnProperty(name="relay.confirmation.validator-enabled", havingValue="true")
public class RelayValidatorController {
    private final RelayConfirmationProperties properties;
    private final ChainSdkRouter chains;
    public RelayValidatorController(RelayConfirmationProperties properties, ChainSdkRouter chains) { this.properties=properties;this.chains=chains; }
    @PostMapping("/internal/relay/endorse")
    public RelayCertificate.Vote endorse(@RequestBody RelayCertificate.Request request) throws Exception {
        try {
            if (request == null || !properties.getCommitteeVersion().equals(request.getCommitteeVersion())) throw new IllegalArgumentException("委员会版本不匹配");
            ConfirmationProof.checkTime(request.getIssuedAt(), System.currentTimeMillis()/1000);
            if (request.getCoordinatorSignature() == null || request.getCoordinatorSignature().length()>1024
                    || !RsaUtil.verify(ConfirmationProof.requestMessage(request), request.getCoordinatorSignature(), properties.getCoordinatorPublicKey()))
                throw new IllegalArgumentException("协调端签名不合法");
        } catch (Exception error) { throw new ResponseStatusException(HttpStatus.FORBIDDEN,"核验请求未获授权"); }
        chains.verifyConfirmedSource(request.getPayload());
        RelayCertificate.Vote vote=new RelayCertificate.Vote();vote.setNodeId(properties.getValidatorId());
        vote.setSignature(RsaUtil.sign(ConfirmationProof.message(request.getPayload(),request.getCommitteeVersion(),request.getIssuedAt()),properties.getValidatorPrivateKey()));
        return vote;
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String,String>> rejected(Exception error) {
        HttpStatus status=error instanceof ResponseStatusException?((ResponseStatusException)error).getStatus():HttpStatus.BAD_GATEWAY;
        return ResponseEntity.status(status).body(Collections.singletonMap("error",status==HttpStatus.FORBIDDEN?"核验请求未获授权":"源链核验未完成"));
    }
}
