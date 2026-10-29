package main

import (
	"encoding/json"
	"os"
	"testing"
)

type quorumVector struct {
	Policy     relayCommittee    `json:"policy"`
	Arguments  map[string]string `json:"arguments"`
	Signatures map[string]string `json:"signatures"`
}

func quorumSetup(t *testing.T) (*TraceAnchorContract, *memorySDK, quorumVector) {
	t.Helper()
	raw, err := os.ReadFile("testdata/quorum-vector.json")
	if err != nil {
		t.Fatal(err)
	}
	var v quorumVector
	if err = json.Unmarshal(raw, &v); err != nil {
		t.Fatal(err)
	}
	c, m := setup(t)
	keys, _ := json.Marshal(map[string]string{"sub-production": v.Policy.Members["node0"].PublicKey})
	m.args = map[string][]byte{"local_chain_code": []byte("main-regulator"), "relay_public_keys": keys}
	if r := c.InitContract(); r.Status != 0 {
		t.Fatal(r.Message)
	}
	m.sender = "creator"
	policy, _ := json.Marshal(v.Policy)
	m.args = map[string][]byte{"committee": policy}
	if r := c.InvokeContract("configure_relay_committee"); r.Status != 0 {
		t.Fatal(r.Message)
	}
	m.sender = "writer"
	setQuorumArgs(m, v, []string{"node0", "node1", "node2"})
	return c, m, v
}
func setQuorumArgs(m *memorySDK, v quorumVector, ids []string) {
	m.args = map[string][]byte{}
	for k, value := range v.Arguments {
		m.args[k] = []byte(value)
	}
	signatures := []string{}
	for _, id := range ids {
		signatures = append(signatures, v.Signatures[id])
	}
	m.args["validator_ids"], _ = json.Marshal(ids)
	m.args["validator_signatures"], _ = json.Marshal(signatures)
}
func TestJavaQuorumVectorAcceptedAndReplayIsIdempotent(t *testing.T) {
	c, m, _ := quorumSetup(t)
	for i := 0; i < 2; i++ {
		if r := c.InvokeContract("receive_cross_anchor_quorum"); r.Status != 0 {
			t.Fatal(r.Message)
		}
	}
	if len(m.state[stateKey("xc/sub-production/quorum-source-001")+"|v2"]) == 0 {
		t.Fatal("missing accepted cross anchor")
	}
}
func TestQuorumRejectsInsufficientDuplicateUnknownAndInvalidVotes(t *testing.T) {
	c, m, v := quorumSetup(t)
	for _, ids := range [][]string{{"node0", "node1"}, {"node0", "node0", "node1"}, {"node0", "node1", "unknown"}} {
		setQuorumArgs(m, v, ids)
		if c.InvokeContract("receive_cross_anchor_quorum").Status == 0 {
			t.Fatal("invalid signer set accepted")
		}
	}
	setQuorumArgs(m, v, []string{"node0", "node1", "node2"})
	m.args["data_hash"] = []byte("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb")
	if c.InvokeContract("receive_cross_anchor_quorum").Status == 0 {
		t.Fatal("tampered content accepted")
	}
	if len(m.state[stateKey("xc/sub-production/quorum-source-001")+"|v2"]) > 0 {
		t.Fatal("failed votes wrote business state")
	}
}
func TestSingleRelayCannotBypassConfiguredCommittee(t *testing.T) {
	c, m, v := quorumSetup(t)
	m.args = map[string][]byte{}
	for key, value := range v.Arguments {
		if key != "committee_version" && key != "proof_type" {
			m.args[key] = []byte(value)
		}
	}
	m.args["signature"] = []byte(v.Signatures["node0"])
	if c.InvokeContract("receive_cross_anchor").Status == 0 {
		t.Fatal("legacy relay bypassed quorum")
	}
}
func TestCommitteeConfigurationIsCreatorOnlyAndCannotDowngrade(t *testing.T) {
	c, m, v := quorumSetup(t)
	raw, _ := json.Marshal(v.Policy)
	m.args = map[string][]byte{"committee": raw}
	if c.InvokeContract("configure_relay_committee").Status == 0 {
		t.Fatal("writer reconfigured committee")
	}
	m.sender = "creator"
	if c.InvokeContract("configure_relay_committee").Status == 0 {
		t.Fatal("same version accepted")
	}
	v.Policy.Version = "2"
	v.Policy.Members["node1"] = v.Policy.Members["node0"]
	raw, _ = json.Marshal(v.Policy)
	m.args["committee"] = raw
	if c.InvokeContract("configure_relay_committee").Status == 0 {
		t.Fatal("duplicate organization/key accepted")
	}
}
func TestQuorumRejectsWrongCommitteeAndExpiredTime(t *testing.T) {
	c, m, v := quorumSetup(t)
	m.args["committee_version"] = []byte("2")
	if c.InvokeContract("receive_cross_anchor_quorum").Status == 0 {
		t.Fatal("wrong version accepted")
	}
	setQuorumArgs(m, v, []string{"node0", "node1", "node2"})
	m.args["issued_at"] = []byte("1699990000")
	if c.InvokeContract("receive_cross_anchor_quorum").Status == 0 {
		t.Fatal("expired proof accepted")
	}
}
func TestCommitteePolicyCanBeReadWithoutWriterPermission(t *testing.T) {
	c, m, v := quorumSetup(t)
	m.org = "outsider"
	response := c.InvokeContract("relay_policy")
	var actual relayCommittee
	if response.Status != 0 || json.Unmarshal(response.Payload, &actual) != nil || actual.Version != v.Policy.Version || actual.Threshold != 3 || len(actual.Members) != 4 {
		t.Fatal("policy query mismatch")
	}
}
