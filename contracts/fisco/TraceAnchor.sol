// SPDX-License-Identifier: MIT
pragma solidity ^0.8.11;

import "./RsaSha256.sol";

/** FISCO BCOS 3.x EVM版本，与长安链Go合约使用相同的业务字段与中继签名原文。 */
contract TraceAnchor {
    struct Anchor { string dataHash; string objectCid; string partsDid; bool exists; }
    struct Credential { string dataHash; address writer; bool revoked; }
    struct RelayKey { bytes modulus; bytes exponent; }
    struct CrossAnchor { bytes32 contentHash; string dataHash; string objectCid; string partsDid; bool exists; }

    address public immutable owner;
    string private chainCode;
    mapping(address => bool) public writers;
    mapping(bytes32 => Anchor) private anchors;
    mapping(bytes32 => Credential) private credentials;
    mapping(string => RelayKey) private relayKeys;
    mapping(bytes32 => CrossAnchor) private crossAnchors;
    string private committeeVersion = "0";
    uint256 private committeeThreshold;
    string[] private committeeIds;
    mapping(string => RelayKey) private validatorKeys;
    mapping(string => string) private validatorOrganizations;

    event AnchorSaved(string businessType, string businessId, string dataHash);
    event CredentialChanged(string partsDid, string vcId, bool revoked);
    event CrossAnchorReceived(string sourceChain, string sourceTx, bytes32 contentHash);
    event WriterChanged(address writer, bool enabled);
    event RelayKeyChanged(string sourceChain);
    event RelayCommitteeChanged(string version, uint256 threshold);

    modifier onlyOwner() { require(msg.sender == owner, "owner required"); _; }
    modifier onlyWriter() { require(msg.sender == owner || writers[msg.sender], "writer not authorized"); _; }

    constructor(string memory localChainCode, address[] memory initialWriters,
        string[] memory sourceChains, bytes[] memory moduli, bytes[] memory exponents)
    {
        require(identifier(localChainCode, 64), "invalid chain code");
        require(sourceChains.length == moduli.length && moduli.length == exponents.length, "key arrays differ");
        owner = msg.sender;
        chainCode = localChainCode;
        for (uint256 i; i < initialWriters.length; i++) {
            require(initialWriters[i] != address(0), "zero writer");
            writers[initialWriters[i]] = true;
        }
        for (uint256 i; i < sourceChains.length; i++) setKey(sourceChains[i], moduli[i], exponents[i]);
    }

    function contract_info() external view returns (string memory, string memory) {
        return ("parts-trace-anchor/v2", chainCode);
    }

    function set_writer(address writer, bool enabled) external onlyOwner {
        require(writer != address(0), "zero writer");
        writers[writer] = enabled;
        emit WriterChanged(writer, enabled);
    }

    function set_relay_key(string calldata sourceChain, bytes calldata modulus, bytes calldata exponent) external onlyOwner {
        setKey(sourceChain, modulus, exponent);
    }

    function get_relay_key(string calldata sourceChain) external view returns (bytes memory, bytes memory) {
        RelayKey storage key = relayKeys[sourceChain];
        return (key.modulus, key.exponent);
    }

    function setKey(string memory sourceChain, bytes memory modulus, bytes memory exponent) private {
        require(identifier(sourceChain, 64), "invalid source chain");
        validateKey(modulus, exponent);
        relayKeys[sourceChain] = RelayKey(modulus, exponent);
        emit RelayKeyChanged(sourceChain);
    }

    function validateKey(bytes memory modulus, bytes memory exponent) private pure {
        require(modulus.length >= 256 && modulus.length <= 512 && uint8(modulus[0]) >= 128
            && uint8(modulus[modulus.length - 1]) % 2 == 1, "RSA modulus must be 2048-4096 bits");
        require(exponent.length == 3 && exponent[0] == 0x01 && exponent[1] == 0x00 && exponent[2] == 0x01, "RSA exponent must be 65537");
    }

    function configure_relay_committee(string calldata version, string[] calldata ids,
        string[] calldata organizations, bytes[] calldata moduli, bytes[] calldata exponents) external onlyOwner
    {
        require(bytes(version).length <= 9 && bytes(version).length > 0 && bytes(version)[0] != 0x30
            && number(version) == number(committeeVersion) + 1, "committee version must increase by one");
        require(ids.length >= 4 && ids.length <= 10 && (ids.length - 1) % 3 == 0
            && organizations.length == ids.length && moduli.length == ids.length && exponents.length == ids.length, "invalid committee arrays");
        for (uint256 i; i < committeeIds.length; i++) {
            delete validatorKeys[committeeIds[i]]; delete validatorOrganizations[committeeIds[i]];
        }
        delete committeeIds;
        for (uint256 i; i < ids.length; i++) {
            require(identifier(ids[i],64) && identifier(organizations[i],64) && validatorKeys[ids[i]].modulus.length == 0, "invalid or duplicate validator");
            validateKey(moduli[i],exponents[i]);
            for (uint256 j; j < i; j++) {
                require(!equal(organizations[i],organizations[j]) && sha256(moduli[i]) != sha256(moduli[j]), "duplicate organization or RSA key");
            }
            validatorKeys[ids[i]] = RelayKey(moduli[i],exponents[i]);validatorOrganizations[ids[i]]=organizations[i];committeeIds.push(ids[i]);
        }
        committeeVersion=version;committeeThreshold=2*((ids.length-1)/3)+1;
        emit RelayCommitteeChanged(version,committeeThreshold);
    }

    function relay_policy() external view returns (string memory version, uint256 threshold, string[] memory ids,
        string[] memory organizations, bytes[] memory moduli, bytes[] memory exponents)
    {
        version=committeeVersion;threshold=committeeThreshold;ids=committeeIds;
        organizations=new string[](ids.length);moduli=new bytes[](ids.length);exponents=new bytes[](ids.length);
        for(uint256 i;i<ids.length;i++) {
            organizations[i]=validatorOrganizations[ids[i]];moduli[i]=validatorKeys[ids[i]].modulus;exponents[i]=validatorKeys[ids[i]].exponent;
        }
    }

    function save_anchor(string calldata businessType, string calldata businessId, string calldata dataHash,
        string calldata objectCid, string calldata partsDid) external onlyWriter
    {
        require(identifier(businessType, 64) && decimal(businessId), "invalid business identity");
        require(validHash(dataHash), "SHA-256 hash required");
        bytes32 key = sha256(abi.encode(businessType, businessId));
        Anchor storage old = anchors[key];
        if (old.exists) {
            require(equal(old.dataHash, dataHash) && equal(old.objectCid, objectCid) && equal(old.partsDid, partsDid), "conflicting anchor");
            return;
        }
        anchors[key] = Anchor(dataHash, objectCid, partsDid, true);
        emit AnchorSaved(businessType, businessId, dataHash);
    }

    function get_anchor(string calldata businessType, string calldata businessId) external view returns (string memory, string memory, string memory) {
        Anchor storage anchor = anchors[sha256(abi.encode(businessType, businessId))];
        return (anchor.dataHash, anchor.objectCid, anchor.partsDid);
    }

    function append_vc(string calldata partsDid, string calldata vcId, string calldata vcHash) external onlyWriter {
        require(validDid(partsDid) && identifier(vcId, 128) && validHash(vcHash), "invalid credential");
        Credential storage credential = credentials[sha256(abi.encode(partsDid, vcId))];
        require(!credential.revoked, "credential revoked");
        if (credential.writer != address(0)) {
            require(equal(credential.dataHash, vcHash), "conflicting credential");
            return;
        }
        credential.dataHash = vcHash;
        credential.writer = msg.sender;
        emit CredentialChanged(partsDid, vcId, false);
    }

    function revoke_vc(string calldata partsDid, string calldata vcId) external onlyWriter {
        require(validDid(partsDid) && identifier(vcId, 128), "invalid credential identity");
        Credential storage credential = credentials[sha256(abi.encode(partsDid, vcId))];
        require(credential.writer == address(0) || credential.writer == msg.sender || msg.sender == owner, "only credential writer may revoke");
        if (credential.writer == address(0)) credential.writer = msg.sender;
        credential.revoked = true;
        emit CredentialChanged(partsDid, vcId, true);
    }

    function get_vc(string calldata partsDid, string calldata vcId) external view returns (string memory) {
        Credential storage credential = credentials[sha256(abi.encode(partsDid, vcId))];
        return credential.revoked ? "0" : credential.dataHash;
    }

    // 字段顺序：源链、源交易、源高度、业务类型、业务编号、主体、摘要、CID、目标链、时间。
    function receive_cross_anchor(string[] calldata fields, bytes calldata signature) external onlyWriter {
        require(committeeThreshold == 0, "committee confirmation required");
        validateCrossFields(fields);
        RelayKey storage relayKey = relayKeys[fields[0]];
        require(RsaSha256.verify(sha256(message(fields,"")), signature, relayKey.modulus, relayKey.exponent), "relay signature rejected");
        storeCrossAnchor(fields);
    }

    function receive_cross_anchor_quorum(string[] calldata fields, string calldata version,
        string[] calldata ids, bytes[] calldata signatures) external onlyWriter
    {
        require(committeeThreshold > 0 && equal(version,committeeVersion) && ids.length >= committeeThreshold
            && ids.length <= committeeIds.length && signatures.length == ids.length, "committee version or quorum mismatch");
        validateCrossFields(fields);
        bytes32 digest=sha256(message(fields,version));
        for(uint256 i;i<ids.length;i++) {
            for(uint256 j;j<i;j++)require(!equal(ids[i],ids[j]),"duplicate validator");
            RelayKey storage key=validatorKeys[ids[i]];
            require(RsaSha256.verify(digest,signatures[i],key.modulus,key.exponent),"validator signature rejected");
        }
        storeCrossAnchor(fields);
    }

    function validateCrossFields(string[] calldata fields) private view {
        require(fields.length == 10 && equal(fields[8], chainCode), "invalid target or fields");
        require(identifier(fields[0], 64) && bytes(fields[1]).length > 0 && bytes(fields[1]).length <= 256
            && decimal(fields[2]) && identifier(fields[3], 64) && decimal(fields[4]) && validDid(fields[5]) && validHash(fields[6]), "invalid cross-chain identity");
        for (uint256 i; i < fields.length; i++) require(clean(fields[i]), "invalid proof field");
        uint256 nowSeconds = block.timestamp > 1000000000000 ? block.timestamp / 1000 : block.timestamp;
        uint256 issuedAt = number(fields[9]);
        require(issuedAt <= nowSeconds + 60 && (issuedAt >= nowSeconds || nowSeconds - issuedAt <= 300), "relay message expired");
        require(relayKeys[fields[0]].modulus.length > 0,"untrusted source chain");
    }

    function storeCrossAnchor(string[] calldata fields) private {
        bytes32 key = sha256(abi.encode(fields[0], fields[1]));
        string[] memory contentFields = new string[](9);
        for (uint256 i; i < 9; i++) contentFields[i] = fields[i];
        bytes32 content = sha256(abi.encode(contentFields));
        CrossAnchor storage old = crossAnchors[key];
        if (old.exists) { require(old.contentHash == content, "conflicting replay"); return; }
        crossAnchors[key] = CrossAnchor(content, fields[6], fields[7], fields[5], true);
        emit CrossAnchorReceived(fields[0], fields[1], content);
    }

    function get_cross_anchor(string calldata sourceChain, string calldata sourceTx) external view returns (string memory, string memory, string memory) {
        CrossAnchor storage anchor = crossAnchors[sha256(abi.encode(sourceChain, sourceTx))];
        return (anchor.dataHash, anchor.objectCid, anchor.partsDid);
    }

    function message(string[] calldata f, string memory version) private pure returns (bytes memory) {
        string[10] memory names = ["business_id", "business_type", "data_hash", "issued_at", "object_cid", "parts_did", "source_block_height", "source_chain_id", "source_tx_hash", "target_chain_id"];
        uint256[10] memory indices = [uint256(4), 3, 6, 9, 7, 5, 2, 0, 1, 8];
        bytes memory result;
        for (uint256 i; i < 10; i++) {
            if(bytes(version).length>0 && i==2)result=abi.encodePacked(result,"committee_version=",version,"\n");
            if(bytes(version).length>0 && i==6)result=abi.encodePacked(result,"proof_type=relay-quorum/v1\n");
            result = abi.encodePacked(result, names[i], "=", f[indices[i]], "\n");
        }
        return result;
    }

    function number(string memory value) private pure returns (uint256 result) {
        require(decimal(value), "invalid number");
        bytes memory raw = bytes(value);
        for (uint256 i; i < raw.length; i++) result = result * 10 + uint8(raw[i]) - 48;
    }

    function decimal(string memory value) private pure returns (bool) {
        bytes memory raw = bytes(value);
        if (raw.length == 0 || raw.length > 20) return false;
        for (uint256 i; i < raw.length; i++) if (raw[i] < 0x30 || raw[i] > 0x39) return false;
        return true;
    }

    function validDid(string memory value) private pure returns (bool) {
        bytes memory raw = bytes(value);
        if (raw.length < 9 || raw.length > 254 || raw[0] != 0x64 || raw[1] != 0x69 || raw[2] != 0x64 || raw[3] != 0x3a) return false;
        for (uint256 i = 4; i < raw.length; i++) if (!identifierByte(raw[i]) && raw[i] != 0x3a) return false;
        return true;
    }

    function identifier(string memory value, uint256 maxSize) private pure returns (bool) {
        bytes memory raw = bytes(value);
        if (raw.length == 0 || raw.length > maxSize) return false;
        for (uint256 i; i < raw.length; i++) if (!identifierByte(raw[i])) return false;
        return true;
    }

    function identifierByte(bytes1 c) private pure returns (bool) {
        return (c >= 0x30 && c <= 0x39) || (c >= 0x41 && c <= 0x5a) || (c >= 0x61 && c <= 0x7a) || c == 0x2d || c == 0x5f;
    }

    function validHash(string memory value) private pure returns (bool) {
        bytes memory raw = bytes(value);
        if (raw.length != 64) return false;
        for (uint256 i; i < 64; i++) if (!((raw[i] >= 0x30 && raw[i] <= 0x39) || (raw[i] >= 0x61 && raw[i] <= 0x66))) return false;
        return true;
    }

    function clean(string memory value) private pure returns (bool) {
        bytes memory raw = bytes(value);
        if (raw.length > 1024) return false;
        for (uint256 i; i < raw.length; i++) if (raw[i] == 0x0a || raw[i] == 0x0d) return false;
        return true;
    }

    function equal(string memory left, string memory right) private pure returns (bool) {
        return sha256(bytes(left)) == sha256(bytes(right));
    }
}
