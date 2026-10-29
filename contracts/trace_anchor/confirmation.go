package main

import (
	"chainmaker.org/chainmaker/contract-sdk-go/v2/pb/protogo"
	"chainmaker.org/chainmaker/contract-sdk-go/v2/sdk"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"regexp"
	"strconv"
)

type committeeMember struct {
	Organization string `json:"organization"`
	PublicKey    string `json:"publicKey"`
}
type relayCommittee struct {
	Version   string                     `json:"version"`
	Threshold int                        `json:"threshold"`
	Members   map[string]committeeMember `json:"members"`
}

func committee() (relayCommittee, error) {
	c := relayCommittee{Version: "0", Members: map[string]committeeMember{}}
	raw, err := getState("relay_committee", field)
	if err != nil {
		return c, err
	}
	if len(raw) > 0 {
		err = json.Unmarshal(raw, &c)
	}
	return c, err
}
func (t *TraceAnchorContract) configureRelayCommittee() protogo.Response {
	if !creator() {
		return sdk.Error("only contract creator may configure committee")
	}
	var next relayCommittee
	if err := json.Unmarshal(sdk.Instance.GetArgs()["committee"], &next); err != nil {
		return sdk.Error("invalid committee")
	}
	old, err := committee()
	if err != nil {
		return sdk.Error(err.Error())
	}
	version, err := strconv.ParseUint(next.Version, 10, 32)
	oldVersion, _ := strconv.ParseUint(old.Version, 10, 32)
	if err != nil || version != oldVersion+1 || version > 999999999 || strconv.FormatUint(version, 10) != next.Version {
		return sdk.Error("committee version must increase by one")
	}
	count := len(next.Members)
	if count < 4 || count > 10 || (count-1)%3 != 0 || next.Threshold != 2*((count-1)/3)+1 {
		return sdk.Error("invalid committee size or threshold")
	}
	orgs, keys := map[string]bool{}, map[string]bool{}
	identifier := regexp.MustCompile(`^[a-zA-Z0-9_-]{1,64}$`)
	for id, member := range next.Members {
		if !identifier.MatchString(id) || !identifier.MatchString(member.Organization) || orgs[member.Organization] {
			return sdk.Error("invalid or duplicate organization")
		}
		key, err := parsePublicKey(member.PublicKey)
		if err != nil || key.E != 65537 || key.N.BitLen() > 4096 || key.N.BitLen()%8 != 0 {
			return sdk.Error("invalid committee RSA key")
		}
		digest := sha256.Sum256(key.N.Bytes())
		fingerprint := hex.EncodeToString(digest[:])
		if keys[fingerprint] {
			return sdk.Error("duplicate committee key")
		}
		keys[fingerprint] = true
		orgs[member.Organization] = true
	}
	raw, _ := json.Marshal(next)
	if err := putState("relay_committee", field, raw); err != nil {
		return sdk.Error(err.Error())
	}
	return sdk.SuccessResponse
}
func verifyCommittee(values map[string]string, ids, signatures []string, c relayCommittee, now int64) error {
	if c.Threshold == 0 || values["committee_version"] != c.Version || len(ids) < c.Threshold || len(ids) > len(c.Members) || len(ids) != len(signatures) {
		return errors.New("committee version or quorum mismatch")
	}
	seen := map[string]bool{}
	for i, id := range ids {
		member, exists := c.Members[id]
		if !exists || seen[id] {
			return errors.New("unknown or duplicate validator")
		}
		seen[id] = true
		if err := verifyRelay(values, signatures[i], member.PublicKey, now); err != nil {
			return errors.New("validator signature rejected")
		}
	}
	return nil
}
