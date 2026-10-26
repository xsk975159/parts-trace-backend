// 零部件存证合约。部署参数：local_chain_code、writer_orgs(JSON数组)、relay_public_keys(JSON对象)。
// 跨链信任模型为授权中继签名；不把中继签名表述为源链共识或Merkle证明。
package main

import (
	"bytes"
	"chainmaker.org/chainmaker/contract-sdk-go/v2/pb/protogo"
	"chainmaker.org/chainmaker/contract-sdk-go/v2/sandbox"
	"chainmaker.org/chainmaker/contract-sdk-go/v2/sdk"
	"crypto"
	"crypto/rsa"
	"crypto/sha256"
	"crypto/x509"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"errors"
	"regexp"
	"sort"
	"strconv"
	"strings"
)

const field = "v2"

type TraceAnchorContract struct{}

func (t *TraceAnchorContract) InitContract() protogo.Response { return t.configure() }
func (t *TraceAnchorContract) UpgradeContract() protogo.Response {
	if !creator() {
		return sdk.Error("only contract creator may upgrade configuration")
	}
	return t.configure()
}
func creator() bool {
	sender, e1 := sdk.Instance.GetSenderPk()
	owner, e2 := sdk.Instance.GetCreatorPk()
	return e1 == nil && e2 == nil && sender != "" && sender == owner
}
func (t *TraceAnchorContract) configure() protogo.Response {
	args := sdk.Instance.GetArgs()
	code := string(args["local_chain_code"])
	if !regexp.MustCompile(`^[a-zA-Z0-9_-]{1,64}$`).MatchString(code) {
		return sdk.Error("local_chain_code required")
	}
	var orgs []string
	if len(args["writer_orgs"]) == 0 {
		org, err := sdk.Instance.GetCreatorOrgId()
		if err != nil || org == "" {
			return sdk.Error("creator org unavailable")
		}
		orgs = []string{org}
	} else if err := json.Unmarshal(args["writer_orgs"], &orgs); err != nil || len(orgs) == 0 {
		return sdk.Error("invalid writer_orgs")
	}
	keys := map[string]string{}
	if len(args["relay_public_keys"]) > 0 {
		if err := json.Unmarshal(args["relay_public_keys"], &keys); err != nil {
			return sdk.Error("invalid relay_public_keys")
		}
		for source, key := range keys {
			if source == "" {
				return sdk.Error("empty source chain")
			}
			if _, err := parsePublicKey(key); err != nil {
				return sdk.Error("invalid relay RSA public key")
			}
		}
	}
	config, _ := json.Marshal(map[string]interface{}{"code": code, "orgs": orgs, "keys": keys})
	if err := sdk.Instance.PutStateByte("config", field, config); err != nil {
		return sdk.Error(err.Error())
	}
	return sdk.SuccessResponse
}

type configuration struct {
	Code string            `json:"code"`
	Orgs []string          `json:"orgs"`
	Keys map[string]string `json:"keys"`
}

func config() (configuration, error) {
	var c configuration
	raw, err := sdk.Instance.GetStateByte("config", field)
	if err != nil {
		return c, err
	}
	if len(raw) == 0 {
		return c, errors.New("v2 contract configuration is missing")
	}
	err = json.Unmarshal(raw, &c)
	return c, err
}
func authorized() error {
	c, err := config()
	if err != nil {
		return err
	}
	org, err := sdk.Instance.GetSenderOrgId()
	if err != nil {
		return err
	}
	for _, allowed := range c.Orgs {
		if allowed == org {
			return nil
		}
	}
	return errors.New("writer organization is not authorized")
}
func (t *TraceAnchorContract) InvokeContract(method string) protogo.Response {
	switch method {
	case "contract_info":
		c, err := config()
		if err != nil {
			return sdk.Error(err.Error())
		}
		raw, _ := json.Marshal(map[string]string{"schema": "parts-trace-anchor/v2", "chainCode": c.Code})
		return sdk.Success(raw)
	case "get_anchor":
		args := sdk.Instance.GetArgs()
		key, err := anchorKey(args)
		if err != nil {
			return sdk.Error(err.Error())
		}
		return read(key, field)
	case "get_vc":
		args := sdk.Instance.GetArgs()
		if err := vcArgs(args); err != nil {
			return sdk.Error(err.Error())
		}
		return read("didvc/"+string(args["parts_did"]), string(args["vc_id"]))
	case "get_cross_anchor":
		args := sdk.Instance.GetArgs()
		return read("xc/"+string(args["source_chain_id"])+"/"+string(args["source_tx_hash"]), field)
	}
	if err := authorized(); err != nil {
		return sdk.Error(err.Error())
	}
	switch method {
	case "save_anchor":
		return t.saveAnchor()
	case "append_vc":
		return t.appendVc()
	case "revoke_vc":
		return t.revokeVc()
	case "receive_cross_anchor":
		return t.receiveCrossAnchor()
	default:
		return sdk.Error("unknown method")
	}
}
func read(key, column string) protogo.Response {
	raw, err := sdk.Instance.GetStateByte(key, column)
	if err != nil {
		return sdk.Error(err.Error())
	}
	return sdk.Success(raw)
}
func anchorKey(args map[string][]byte) (string, error) {
	if !regexp.MustCompile(`^[a-zA-Z0-9_-]{1,64}$`).Match(args["business_type"]) || !regexp.MustCompile(`^[0-9]{1,20}$`).Match(args["business_id"]) {
		return "", errors.New("invalid business identity")
	}
	return "anchor/" + string(args["business_type"]) + "/" + string(args["business_id"]), nil
}
func validHash(raw []byte) bool { return regexp.MustCompile(`^[a-f0-9]{64}$`).Match(raw) }
func (t *TraceAnchorContract) saveAnchor() protogo.Response {
	args := sdk.Instance.GetArgs()
	key, err := anchorKey(args)
	if err != nil {
		return sdk.Error(err.Error())
	}
	if !validHash(args["data_hash"]) {
		return sdk.Error("SHA-256 data_hash required")
	}
	data := map[string]string{}
	for _, name := range []string{"business_type", "business_id", "data_hash", "object_cid", "parts_did"} {
		data[name] = string(args[name])
	}
	raw, _ := json.Marshal(data)
	old, err := sdk.Instance.GetStateByte(key, field)
	if err != nil {
		return sdk.Error(err.Error())
	}
	if len(old) > 0 {
		if bytes.Equal(old, raw) {
			return sdk.SuccessResponse
		}
		return sdk.Error("anchor identity already has different immutable content")
	}
	if err := sdk.Instance.PutStateByte(key, field, raw); err != nil {
		return sdk.Error(err.Error())
	}
	return sdk.SuccessResponse
}
func vcArgs(args map[string][]byte) error {
	if !regexp.MustCompile(`^did:[a-zA-Z0-9:_-]{5,250}$`).Match(args["parts_did"]) || !regexp.MustCompile(`^[a-zA-Z0-9_-]{1,128}$`).Match(args["vc_id"]) {
		return errors.New("invalid DID or VC identity")
	}
	return nil
}
func senderFingerprint() (string, error) {
	sender, err := sdk.Instance.GetSenderPk()
	if err != nil || sender == "" {
		return "", errors.New("sender key unavailable")
	}
	hash := sha256.Sum256([]byte(sender))
	return hex.EncodeToString(hash[:]), nil
}
func (t *TraceAnchorContract) appendVc() protogo.Response {
	args := sdk.Instance.GetArgs()
	if err := vcArgs(args); err != nil {
		return sdk.Error(err.Error())
	}
	if !validHash(args["vc_hash"]) {
		return sdk.Error("invalid vc_hash")
	}
	key, id := "didvc/"+string(args["parts_did"]), string(args["vc_id"])
	old, err := sdk.Instance.GetStateByte(key, id)
	if err != nil {
		return sdk.Error(err.Error())
	}
	if len(old) > 0 {
		if bytes.Equal(old, args["vc_hash"]) {
			return sdk.SuccessResponse
		}
		return sdk.Error("VC is revoked or already has different content")
	}
	owner, err := senderFingerprint()
	if err != nil {
		return sdk.Error(err.Error())
	}
	if err := sdk.Instance.PutStateByte("vcowner/"+string(args["parts_did"]), id, []byte(owner)); err != nil {
		return sdk.Error(err.Error())
	}
	if err := sdk.Instance.PutStateByte(key, id, args["vc_hash"]); err != nil {
		return sdk.Error(err.Error())
	}
	return sdk.SuccessResponse
}
func (t *TraceAnchorContract) revokeVc() protogo.Response {
	args := sdk.Instance.GetArgs()
	if err := vcArgs(args); err != nil {
		return sdk.Error(err.Error())
	}
	did, id := string(args["parts_did"]), string(args["vc_id"])
	owner, err := sdk.Instance.GetStateByte("vcowner/"+did, id)
	if err != nil {
		return sdk.Error(err.Error())
	}
	sender, err := senderFingerprint()
	if err != nil {
		return sdk.Error(err.Error())
	}
	if len(owner) > 0 && string(owner) != sender && !creator() {
		return sdk.Error("only VC writer or contract creator may revoke")
	}
	// 撤销墓碑先于追加到达时也必须保留，迟到的追加不能复活该凭证。
	if err := sdk.Instance.PutStateByte("vcowner/"+did, id, []byte(sender)); err != nil {
		return sdk.Error(err.Error())
	}
	if err := sdk.Instance.PutStateByte("didvc/"+did, id, []byte("0")); err != nil {
		return sdk.Error(err.Error())
	}
	return sdk.SuccessResponse
}
func parsePublicKey(value string) (*rsa.PublicKey, error) {
	raw, err := base64.StdEncoding.DecodeString(value)
	if err != nil {
		return nil, err
	}
	key, err := x509.ParsePKIXPublicKey(raw)
	if err != nil {
		return nil, err
	}
	rsaKey, ok := key.(*rsa.PublicKey)
	if !ok || rsaKey.N.BitLen() < 2048 {
		return nil, errors.New("RSA-2048 public key required")
	}
	return rsaKey, nil
}
func proofMessage(values map[string]string) string {
	names := make([]string, 0, len(values))
	for name := range values {
		names = append(names, name)
	}
	sort.Strings(names)
	var b strings.Builder
	for _, name := range names {
		b.WriteString(name)
		b.WriteByte('=')
		b.WriteString(values[name])
		b.WriteByte('\n')
	}
	return b.String()
}
func verifyRelay(values map[string]string, signature, publicKey string, now int64) error {
	issued, err := strconv.ParseInt(values["issued_at"], 10, 64)
	if err != nil || issued < now-300 || issued > now+60 {
		return errors.New("relay message expired or clock invalid")
	}
	for _, value := range values {
		if strings.ContainsAny(value, "\r\n") {
			return errors.New("invalid proof field")
		}
	}
	key, err := parsePublicKey(publicKey)
	if err != nil {
		return err
	}
	sig, err := base64.StdEncoding.DecodeString(signature)
	if err != nil {
		return err
	}
	hash := sha256.Sum256([]byte(proofMessage(values)))
	return rsa.VerifyPKCS1v15(key, crypto.SHA256, hash[:], sig)
}
func (t *TraceAnchorContract) receiveCrossAnchor() protogo.Response {
	args := sdk.Instance.GetArgs()
	c, err := config()
	if err != nil {
		return sdk.Error(err.Error())
	}
	values := map[string]string{}
	for _, name := range []string{"source_chain_id", "source_tx_hash", "source_block_height", "business_type", "business_id", "parts_did", "data_hash", "object_cid", "target_chain_id", "issued_at"} {
		values[name] = string(args[name])
	}
	if values["target_chain_id"] != c.Code || c.Keys[values["source_chain_id"]] == "" || !validHash(args["data_hash"]) || values["source_tx_hash"] == "" || len(values["source_tx_hash"]) > 256 {
		return sdk.Error("untrusted source or invalid target/hash")
	}
	if _, err := anchorKey(args); err != nil {
		return sdk.Error(err.Error())
	}
	if len(args["parts_did"]) == 0 {
		return sdk.Error("subject required")
	}
	stamp, err := sdk.Instance.GetTxTimeStamp()
	if err != nil {
		return sdk.Error(err.Error())
	}
	now, err := strconv.ParseInt(stamp, 10, 64)
	if err != nil {
		return sdk.Error("invalid chain timestamp")
	}
	if now > 1000000000000 {
		now /= 1000
	}
	if err := verifyRelay(values, string(args["signature"]), c.Keys[values["source_chain_id"]], now); err != nil {
		return sdk.Error("relay proof rejected: " + err.Error())
	}
	delete(values, "issued_at")
	raw, _ := json.Marshal(values)
	key := "xc/" + values["source_chain_id"] + "/" + values["source_tx_hash"]
	old, err := sdk.Instance.GetStateByte(key, field)
	if err != nil {
		return sdk.Error(err.Error())
	}
	if len(old) > 0 {
		if bytes.Equal(old, raw) {
			return sdk.SuccessResponse
		}
		return sdk.Error("conflicting replay for source transaction")
	}
	if err := sdk.Instance.PutStateByte(key, field, raw); err != nil {
		return sdk.Error(err.Error())
	}
	return sdk.SuccessResponse
}
func main() {
	if err := sandbox.Start(new(TraceAnchorContract)); err != nil {
		panic(err)
	}
}
