// 零部件溯源 - 链上锚点合约（长安链 Go 合约 → 编译为 WASM 部署）
//
// 方法一览（invoke 的 method 名）：
//   - save_anchor        5.1 单链：业务类型 + 业务ID + 内容哈希（+ 可选 CID / partDID）
//   - append_vc          5.1：partDID 下挂接 vcId → vc 内容哈希
//   - revoke_vc          撤销：写入 "0" 表示失效（验证层据此拒绝）
//   - receive_cross_anchor  5.2 原型：主链接收从链已确认交易的摘要（source_chain_id + source_tx_hash + ...）
//
// 约束：合约内禁止使用 time.Now()、随机数等非确定性 API；时间用 sdk.Instance.GetTxTimeStamp()。
package main

import (
	"bytes"
	"encoding/json"

	"chainmaker.org/chainmaker/contract-sdk-go/v2/pb/protogo"
	"chainmaker.org/chainmaker/contract-sdk-go/v2/sandbox"
	"chainmaker.org/chainmaker/contract-sdk-go/v2/sdk"
)

const (
	methodSaveAnchor       = "save_anchor"
	methodAppendVc         = "append_vc"
	methodRevokeVc         = "revoke_vc"
	methodReceiveCross     = "receive_cross_anchor"
	fieldDefault           = "v1"
	keyPrefixAnchor        = "anchor/"
	keyPrefixCross         = "xc/"
	keyPrefixDidVc         = "didvc/"
)

// TraceAnchorContract 存证 + 轻量 DID-VC 索引 + 跨链摘要接收
type TraceAnchorContract struct{}

func (t *TraceAnchorContract) InitContract() protogo.Response {
	return sdk.SuccessResponse
}

func (t *TraceAnchorContract) UpgradeContract() protogo.Response {
	return sdk.SuccessResponse
}

func (t *TraceAnchorContract) InvokeContract(method string) (result protogo.Response) {
	defer func() {
		if result.Status != 0 {
			sdk.Instance.Warnf(result.Message)
		}
	}()

	switch method {
	case methodSaveAnchor:
		return t.saveAnchor()
	case methodAppendVc:
		return t.appendVc()
	case methodRevokeVc:
		return t.revokeVc()
	case methodReceiveCross:
		return t.receiveCrossAnchor()
	default:
		return sdk.Error("invalid method: " + method)
	}
}

func (t *TraceAnchorContract) saveAnchor() protogo.Response {
	args := sdk.Instance.GetArgs()
	bizType := string(bytesTrim(args["business_type"]))
	bizId := string(bytesTrim(args["business_id"]))
	hash := string(bytesTrim(args["data_hash"]))
	if bizType == "" || bizId == "" || hash == "" {
		return sdk.Error("business_type, business_id, data_hash required")
	}
	cid := string(bytesTrim(args["object_cid"]))
	partDid := string(bytesTrim(args["parts_did"]))
	ts, err := sdk.Instance.GetTxTimeStamp()
	if err != nil {
		return sdk.Error("timestamp: " + err.Error())
	}
	val, _ := json.Marshal(map[string]string{
		"business_type": bizType,
		"business_id":   bizId,
		"data_hash":     hash,
		"object_cid":    cid,
		"parts_did":     partDid,
		"tx_time":       ts,
	})
	key := keyPrefixAnchor + bizType + "/" + bizId
	if err := sdk.Instance.PutStateByte(key, fieldDefault, val); err != nil {
		return sdk.Error(err.Error())
	}
	_ = sdk.Instance.EmitEvent("anchor_saved", []string{bizType, bizId, hash})
	return sdk.SuccessResponse
}

func (t *TraceAnchorContract) appendVc() protogo.Response {
	args := sdk.Instance.GetArgs()
	partDid := string(bytesTrim(args["parts_did"]))
	vcID := string(bytesTrim(args["vc_id"]))
	vcHash := string(bytesTrim(args["vc_hash"]))
	if partDid == "" || vcID == "" || vcHash == "" {
		return sdk.Error("parts_did, vc_id, vc_hash required")
	}
	key := keyPrefixDidVc + partDid
	if err := sdk.Instance.PutStateByte(key, vcID, []byte(vcHash)); err != nil {
		return sdk.Error(err.Error())
	}
	_ = sdk.Instance.EmitEvent("vc_appended", []string{partDid, vcID})
	return sdk.SuccessResponse
}

func (t *TraceAnchorContract) revokeVc() protogo.Response {
	args := sdk.Instance.GetArgs()
	partDid := string(bytesTrim(args["parts_did"]))
	vcID := string(bytesTrim(args["vc_id"]))
	if partDid == "" || vcID == "" {
		return sdk.Error("parts_did, vc_id required")
	}
	key := keyPrefixDidVc + partDid
	if err := sdk.Instance.PutStateByte(key, vcID, []byte("0")); err != nil {
		return sdk.Error(err.Error())
	}
	return sdk.SuccessResponse
}

func (t *TraceAnchorContract) receiveCrossAnchor() protogo.Response {
	args := sdk.Instance.GetArgs()
	srcChain := string(bytesTrim(args["source_chain_id"]))
	srcTx := string(bytesTrim(args["source_tx_hash"]))
	partDid := string(bytesTrim(args["parts_did"]))
	dataHash := string(bytesTrim(args["data_hash"]))
	if srcChain == "" || srcTx == "" || partDid == "" || dataHash == "" {
		return sdk.Error("source_chain_id, source_tx_hash, parts_did, data_hash required")
	}
	cid := string(bytesTrim(args["object_cid"]))
	ts, err := sdk.Instance.GetTxTimeStamp()
	if err != nil {
		return sdk.Error("timestamp: " + err.Error())
	}
	val, _ := json.Marshal(map[string]string{
		"source_chain_id": srcChain,
		"source_tx_hash":  srcTx,
		"parts_did":       partDid,
		"data_hash":       dataHash,
		"object_cid":      cid,
		"tx_time":         ts,
	})
	key := keyPrefixCross + srcChain + "/" + srcTx
	if err := sdk.Instance.PutStateByte(key, fieldDefault, val); err != nil {
		return sdk.Error(err.Error())
	}
	_ = sdk.Instance.EmitEvent("cross_anchor_received", []string{srcChain, srcTx, partDid})
	return sdk.SuccessResponse
}

func bytesTrim(b []byte) []byte {
	if b == nil {
		return []byte{}
	}
	return bytes.TrimSpace(b)
}

func main() {
	if err := sandbox.Start(new(TraceAnchorContract)); err != nil {
		panic(err)
	}
}
