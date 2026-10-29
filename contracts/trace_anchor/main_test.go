package main

import (
	"chainmaker.org/chainmaker/contract-sdk-go/v2/sdk"
	"chainmaker.org/chainmaker/protocol/v2"
	"crypto"
	"crypto/rand"
	"crypto/rsa"
	"crypto/sha256"
	"crypto/x509"
	"encoding/base64"
	"encoding/json"
	"os"
	"strings"
	"testing"
)

type memorySDK struct {
	sdk.SDKInterface
	args   map[string][]byte
	state  map[string][]byte
	org    string
	sender string
}

func (m *memorySDK) GetArgs() map[string][]byte { return m.args }
func (m *memorySDK) GetStateByte(key, field string) ([]byte, error) {
	if err := protocol.CheckKeyFieldStr(key, field); err != nil {
		return nil, err
	}
	return m.state[key+"|"+field], nil
}
func (m *memorySDK) PutStateByte(key, field string, value []byte) error {
	if err := protocol.CheckKeyFieldStr(key, field); err != nil {
		return err
	}
	m.state[key+"|"+field] = append([]byte(nil), value...)
	return nil
}
func (m *memorySDK) GetSenderOrgId() (string, error)  { return m.org, nil }
func (m *memorySDK) GetSenderPk() (string, error)     { return m.sender, nil }
func (m *memorySDK) GetCreatorPk() (string, error)    { return "creator", nil }
func (m *memorySDK) GetCreatorOrgId() (string, error) { return "org1", nil }
func (m *memorySDK) GetTxTimeStamp() (string, error)  { return "1700000000", nil }

func setup(t *testing.T) (*TraceAnchorContract, *memorySDK) {
	previous := sdk.Instance
	m := &memorySDK{state: map[string][]byte{}, org: "org1", sender: "writer", args: map[string][]byte{"local_chain_code": []byte("main-regulator")}}
	sdk.Instance = m
	t.Cleanup(func() { sdk.Instance = previous })
	contract := new(TraceAnchorContract)
	if response := contract.InitContract(); response.Status != 0 {
		t.Fatal(response.Message)
	}
	return contract, m
}
func TestUnauthorizedWriterRejected(t *testing.T) {
	c, m := setup(t)
	m.org = "untrusted"
	m.args = map[string][]byte{"business_type": []byte("vc"), "business_id": []byte("1"), "data_hash": []byte(strings.Repeat("a", 64))}
	if c.InvokeContract("save_anchor").Status == 0 {
		t.Fatal("unauthorized writer accepted")
	}
}
func TestContractInfoReportsSchemaAndConfiguredChain(t *testing.T) {
	c, _ := setup(t)
	response := c.InvokeContract("contract_info")
	if response.Status != 0 {
		t.Fatal(response.Message)
	}
	var info map[string]string
	if err := json.Unmarshal(response.Payload, &info); err != nil {
		t.Fatal(err)
	}
	if info["schema"] != "parts-trace-anchor/v2" || info["chainCode"] != "main-regulator" {
		t.Fatalf("unexpected contract information: %v", info)
	}
}
func TestAnchorRetryIsIdempotentAndConflictingOverwriteRejected(t *testing.T) {
	c, m := setup(t)
	m.args = map[string][]byte{"business_type": []byte("vc"), "business_id": []byte("1"), "data_hash": []byte(strings.Repeat("a", 64))}
	if c.InvokeContract("save_anchor").Status != 0 || c.InvokeContract("save_anchor").Status != 0 {
		t.Fatal("identical retry failed")
	}
	m.args["data_hash"] = []byte(strings.Repeat("b", 64))
	if c.InvokeContract("save_anchor").Status == 0 {
		t.Fatal("conflicting overwrite accepted")
	}
}
func TestRevocationCannotBeUndoneByDelayedAppend(t *testing.T) {
	c, m := setup(t)
	m.args = map[string][]byte{"parts_did": []byte("did:test:unit"), "vc_id": []byte("VC-1"), "vc_hash": []byte(strings.Repeat("a", 64))}
	if c.InvokeContract("append_vc").Status != 0 || c.InvokeContract("revoke_vc").Status != 0 {
		t.Fatal("initial VC operations failed")
	}
	if c.InvokeContract("append_vc").Status == 0 {
		t.Fatal("revoked credential resurrected")
	}
}
func TestOtherWriterCannotRevokeCredential(t *testing.T) {
	c, m := setup(t)
	m.args = map[string][]byte{"parts_did": []byte("did:test:unit"), "vc_id": []byte("VC-1"), "vc_hash": []byte(strings.Repeat("a", 64))}
	if c.InvokeContract("append_vc").Status != 0 {
		t.Fatal("append failed")
	}
	m.sender = "another-writer"
	if c.InvokeContract("revoke_vc").Status == 0 {
		t.Fatal("non-owner revocation accepted")
	}
}
func TestRevocationTombstoneBeforeAppendRemainsRevoked(t *testing.T) {
	c, m := setup(t)
	m.args = map[string][]byte{"parts_did": []byte("did:test:unit"), "vc_id": []byte("VC-1"), "vc_hash": []byte(strings.Repeat("a", 64))}
	if c.InvokeContract("revoke_vc").Status != 0 {
		t.Fatal("tombstone failed")
	}
	if c.InvokeContract("append_vc").Status == 0 {
		t.Fatal("late append accepted")
	}
}
func sign(values map[string]string, key *rsa.PrivateKey) string {
	h := sha256.Sum256([]byte(proofMessage(values)))
	signature, err := rsa.SignPKCS1v15(rand.Reader, key, crypto.SHA256, h[:])
	if err != nil {
		panic(err)
	}
	return base64.StdEncoding.EncodeToString(signature)
}
func TestRelayProofRejectsTamperingAndExpiry(t *testing.T) {
	key, err := rsa.GenerateKey(rand.Reader, 2048)
	if err != nil {
		t.Fatal(err)
	}
	pub, err := x509.MarshalPKIXPublicKey(&key.PublicKey)
	if err != nil {
		t.Fatal(err)
	}
	public := base64.StdEncoding.EncodeToString(pub)
	values := map[string]string{"issued_at": "1700000000", "data_hash": strings.Repeat("a", 64), "target_chain_id": "main-regulator"}
	signature := sign(values, key)
	if err := verifyRelay(values, signature, public, 1700000000); err != nil {
		t.Fatal(err)
	}
	values["data_hash"] = strings.Repeat("b", 64)
	if verifyRelay(values, signature, public, 1700000000) == nil {
		t.Fatal("tampered proof accepted")
	}
	values["data_hash"] = strings.Repeat("a", 64)
	if verifyRelay(values, signature, public, 1700001000) == nil {
		t.Fatal("expired proof accepted")
	}
}
func TestJavaGeneratedProofAndConflictingReplay(t *testing.T) {
	raw, err := os.ReadFile("testdata/relay-vector.json")
	if err != nil {
		t.Fatal(err)
	}
	var vector struct {
		PublicKey string            `json:"publicKey"`
		Arguments map[string]string `json:"arguments"`
	}
	if err = json.Unmarshal(raw, &vector); err != nil {
		t.Fatal(err)
	}
	values := map[string]string{}
	for key, value := range vector.Arguments {
		if key != "signature" {
			values[key] = value
		}
	}
	if err := verifyRelay(values, vector.Arguments["signature"], vector.PublicKey, 1700000000); err != nil {
		t.Fatal("Java/Go proof mismatch:", err)
	}
	c, m := setup(t)
	keys, _ := json.Marshal(map[string]string{"sub-production": vector.PublicKey})
	m.args = map[string][]byte{"local_chain_code": []byte("main-regulator"), "relay_public_keys": keys}
	if c.configure().Status != 0 {
		t.Fatal("configuration failed")
	}
	m.args = map[string][]byte{}
	for key, value := range vector.Arguments {
		m.args[key] = []byte(value)
	}
	if c.InvokeContract("receive_cross_anchor").Status != 0 || c.InvokeContract("receive_cross_anchor").Status != 0 {
		t.Fatal("valid relay/retry rejected")
	}
	m.args["data_hash"] = []byte(strings.Repeat("b", 64))
	if c.InvokeContract("receive_cross_anchor").Status == 0 {
		t.Fatal("conflicting relay accepted")
	}
}

func TestConflictingReplayWithFreshValidSignatureRejected(t *testing.T) {
	key, err := rsa.GenerateKey(rand.Reader, 2048)
	if err != nil {
		t.Fatal(err)
	}
	public, err := x509.MarshalPKIXPublicKey(&key.PublicKey)
	if err != nil {
		t.Fatal(err)
	}
	keys, _ := json.Marshal(map[string]string{"sub-production": base64.StdEncoding.EncodeToString(public)})
	c, m := setup(t)
	m.args = map[string][]byte{"local_chain_code": []byte("main-regulator"), "relay_public_keys": keys}
	if c.configure().Status != 0 {
		t.Fatal("configuration failed")
	}
	values := map[string]string{"source_chain_id": "sub-production", "source_tx_hash": "same-source-transaction", "source_block_height": "12", "business_type": "vc", "business_id": "1", "parts_did": "did:test:unit", "data_hash": strings.Repeat("a", 64), "object_cid": "QmContent", "target_chain_id": "main-regulator", "issued_at": "1700000000"}
	submit := func() {
		m.args = map[string][]byte{}
		for name, value := range values {
			m.args[name] = []byte(value)
		}
		m.args["signature"] = []byte(sign(values, key))
	}
	submit()
	if c.InvokeContract("receive_cross_anchor").Status != 0 {
		t.Fatal("initial relay failed")
	}
	values["data_hash"] = strings.Repeat("b", 64)
	submit()
	response := c.InvokeContract("receive_cross_anchor")
	if response.Status == 0 || !strings.Contains(response.Message, "conflicting replay") {
		t.Fatal("validly signed conflicting replay not rejected:", response.Message)
	}
}

func TestStateKeyUsesChainMakerAllowedCharacters(t *testing.T) {
	for _, key := range []string{"anchor/docker_smoke/1", "didvc/did:parts:one", "xc/sub-production/tx", "vcowner/did:parts:one"} {
		if err := protocol.CheckKeyFieldStr(stateKey(key), field); err != nil {
			t.Fatal(err)
		}
	}
	if stateKey("a/b") == stateKey("a_b") {
		t.Fatal("logical keys collided")
	}
}
