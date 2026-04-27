# 智能合约设计文档

## 1. 合约概述

### 1.1 合约名称
`PartsTraceContract` - 零部件溯源智能合约

### 1.2 合约功能
- 零部件信息注册
- 溯源记录添加
- 零部件信息查询
- 溯源历史查询
- 零部件状态更新

### 1.3 合约版本
- Solidity版本: 0.8.0+
- 长安链版本: v2.3.3

## 2. 数据结构设计

### 2.1 零部件信息结构

```solidity
struct PartInfo {
    string partNo;          // 零部件编号（唯一标识）
    string partName;        // 零部件名称
    string partType;        // 零部件类型
    string manufacturer;    // 生产商名称
    string manufacturerId;  // 生产商ID
    string productionDate;  // 生产日期
    string batchNo;         // 生产批次号
    string specifications;  // 规格参数（JSON字符串）
    string ipfsHash;        // IPFS文件哈希
    uint256 createTime;     // 创建时间戳
    address creator;        // 创建者地址
    bool exists;            // 是否存在标志
}
```

### 2.2 溯源记录结构

```solidity
struct TraceRecord {
    string partNo;          // 零部件编号
    string traceType;       // 溯源类型（生产/质检/入库/出库/安装）
    string operator;        // 操作人
    string operatorId;      // 操作人ID
    string operatorOrg;    // 操作机构
    string location;       // 位置信息
    string description;     // 描述信息
    uint256 operationTime;  // 操作时间戳
    address operatorAddr;   // 操作人地址
}
```

### 2.3 状态枚举

```solidity
enum PartStatus {
    PRODUCED,      // 已生产
    QUALITY_CHECKED, // 已质检
    STORED,        // 已入库
    SHIPPED,       // 已出库
    INSTALLED      // 已安装
}
```

## 3. 合约接口设计

### 3.1 零部件注册

```solidity
/**
 * 注册零部件信息
 * @param partNo 零部件编号
 * @param partName 零部件名称
 * @param partType 零部件类型
 * @param manufacturer 生产商名称
 * @param manufacturerId 生产商ID
 * @param productionDate 生产日期
 * @param batchNo 批次号
 * @param specifications 规格参数（JSON字符串）
 * @param ipfsHash IPFS文件哈希
 * @return 是否成功
 */
function registerPart(
    string memory partNo,
    string memory partName,
    string memory partType,
    string memory manufacturer,
    string memory manufacturerId,
    string memory productionDate,
    string memory batchNo,
    string memory specifications,
    string memory ipfsHash
) public returns (bool);
```

### 3.2 添加溯源记录

```solidity
/**
 * 添加溯源记录
 * @param partNo 零部件编号
 * @param traceType 溯源类型
 * @param operator 操作人
 * @param operatorId 操作人ID
 * @param operatorOrg 操作机构
 * @param location 位置信息
 * @param description 描述信息
 * @return 是否成功
 */
function addTraceRecord(
    string memory partNo,
    string memory traceType,
    string memory operator,
    string memory operatorId,
    string memory operatorOrg,
    string memory location,
    string memory description
) public returns (bool);
```

### 3.3 查询零部件信息

```solidity
/**
 * 查询零部件信息
 * @param partNo 零部件编号
 * @return 零部件信息结构
 */
function getPartInfo(string memory partNo) 
    public 
    view 
    returns (
        string memory partName,
        string memory partType,
        string memory manufacturer,
        string memory productionDate,
        string memory batchNo,
        string memory ipfsHash,
        uint256 createTime
    );
```

### 3.4 查询溯源历史

```solidity
/**
 * 查询零部件溯源历史
 * @param partNo 零部件编号
 * @return 溯源记录数组
 */
function getTraceHistory(string memory partNo) 
    public 
    view 
    returns (TraceRecord[] memory);
```

### 3.5 更新零部件状态

```solidity
/**
 * 更新零部件状态
 * @param partNo 零部件编号
 * @param status 新状态
 * @return 是否成功
 */
function updatePartStatus(
    string memory partNo,
    PartStatus status
) public returns (bool);
```

## 4. 完整合约代码

```solidity
// SPDX-License-Identifier: MIT
pragma solidity ^0.8.0;

contract PartsTraceContract {
    
    // 错误码定义
    uint256 constant CODE_SUCCESS = 0;
    uint256 constant PART_NOT_EXIST = 3001;
    uint256 constant PART_ALREADY_EXIST = 3002;
    uint256 constant INVALID_PARAM = 3003;
    uint256 constant PERMISSION_DENIED = 3004;
    
    // 零部件信息结构
    struct PartInfo {
        string partNo;
        string partName;
        string partType;
        string manufacturer;
        string manufacturerId;
        string productionDate;
        string batchNo;
        string specifications;
        string ipfsHash;
        uint256 createTime;
        address creator;
        PartStatus status;
        bool exists;
    }
    
    // 溯源记录结构
    struct TraceRecord {
        string partNo;
        string traceType;
        string operator;
        string operatorId;
        string operatorOrg;
        string location;
        string description;
        uint256 operationTime;
        address operatorAddr;
    }
    
    // 状态枚举
    enum PartStatus {
        PRODUCED,
        QUALITY_CHECKED,
        STORED,
        SHIPPED,
        INSTALLED
    }
    
    // 存储映射
    mapping(string => PartInfo) private partInfoMap;  // 零部件编号到信息的映射
    mapping(string => TraceRecord[]) private traceHistoryMap;  // 零部件编号到溯源记录的映射
    string[] private partNoList;  // 所有零部件编号列表
    
    // 事件定义
    event PartRegistered(
        string indexed partNo,
        string partName,
        address creator,
        uint256 timestamp
    );
    
    event TraceRecordAdded(
        string indexed partNo,
        string traceType,
        address operator,
        uint256 timestamp
    );
    
    event PartStatusUpdated(
        string indexed partNo,
        PartStatus oldStatus,
        PartStatus newStatus
    );
    
    /**
     * 注册零部件信息
     */
    function registerPart(
        string memory partNo,
        string memory partName,
        string memory partType,
        string memory manufacturer,
        string memory manufacturerId,
        string memory productionDate,
        string memory batchNo,
        string memory specifications,
        string memory ipfsHash
    ) public returns (bool) {
        // 检查零部件是否已存在
        require(!partInfoMap[partNo].exists, "Part already exists");
        
        // 检查参数有效性
        require(bytes(partNo).length > 0, "Part number cannot be empty");
        require(bytes(partName).length > 0, "Part name cannot be empty");
        
        // 创建零部件信息
        PartInfo memory partInfo = PartInfo({
            partNo: partNo,
            partName: partName,
            partType: partType,
            manufacturer: manufacturer,
            manufacturerId: manufacturerId,
            productionDate: productionDate,
            batchNo: batchNo,
            specifications: specifications,
            ipfsHash: ipfsHash,
            createTime: block.timestamp,
            creator: msg.sender,
            status: PartStatus.PRODUCED,
            exists: true
        });
        
        // 保存零部件信息
        partInfoMap[partNo] = partInfo;
        partNoList.push(partNo);
        
        // 添加初始溯源记录（生产记录）
        TraceRecord memory initialTrace = TraceRecord({
            partNo: partNo,
            traceType: "生产",
            operator: manufacturer,
            operatorId: manufacturerId,
            operatorOrg: manufacturer,
            location: "",
            description: "零部件生产完成",
            operationTime: block.timestamp,
            operatorAddr: msg.sender
        });
        traceHistoryMap[partNo].push(initialTrace);
        
        // 触发事件
        emit PartRegistered(partNo, partName, msg.sender, block.timestamp);
        
        return true;
    }
    
    /**
     * 添加溯源记录
     */
    function addTraceRecord(
        string memory partNo,
        string memory traceType,
        string memory operator,
        string memory operatorId,
        string memory operatorOrg,
        string memory location,
        string memory description
    ) public returns (bool) {
        // 检查零部件是否存在
        require(partInfoMap[partNo].exists, "Part does not exist");
        
        // 检查参数有效性
        require(bytes(traceType).length > 0, "Trace type cannot be empty");
        require(bytes(operator).length > 0, "Operator cannot be empty");
        
        // 创建溯源记录
        TraceRecord memory traceRecord = TraceRecord({
            partNo: partNo,
            traceType: traceType,
            operator: operator,
            operatorId: operatorId,
            operatorOrg: operatorOrg,
            location: location,
            description: description,
            operationTime: block.timestamp,
            operatorAddr: msg.sender
        });
        
        // 保存溯源记录
        traceHistoryMap[partNo].push(traceRecord);
        
        // 触发事件
        emit TraceRecordAdded(partNo, traceType, msg.sender, block.timestamp);
        
        return true;
    }
    
    /**
     * 查询零部件信息
     */
    function getPartInfo(string memory partNo) 
        public 
        view 
        returns (
            string memory partName,
            string memory partType,
            string memory manufacturer,
            string memory productionDate,
            string memory batchNo,
            string memory ipfsHash,
            uint256 createTime,
            PartStatus status
        ) 
    {
        require(partInfoMap[partNo].exists, "Part does not exist");
        
        PartInfo memory partInfo = partInfoMap[partNo];
        
        return (
            partInfo.partName,
            partInfo.partType,
            partInfo.manufacturer,
            partInfo.productionDate,
            partInfo.batchNo,
            partInfo.ipfsHash,
            partInfo.createTime,
            partInfo.status
        );
    }
    
    /**
     * 查询溯源历史记录数量
     */
    function getTraceRecordCount(string memory partNo) 
        public 
        view 
        returns (uint256) 
    {
        require(partInfoMap[partNo].exists, "Part does not exist");
        return traceHistoryMap[partNo].length;
    }
    
    /**
     * 查询指定索引的溯源记录
     */
    function getTraceRecordByIndex(string memory partNo, uint256 index) 
        public 
        view 
        returns (
            string memory traceType,
            string memory operator,
            string memory operatorOrg,
            string memory location,
            string memory description,
            uint256 operationTime
        ) 
    {
        require(partInfoMap[partNo].exists, "Part does not exist");
        require(index < traceHistoryMap[partNo].length, "Index out of range");
        
        TraceRecord memory record = traceHistoryMap[partNo][index];
        
        return (
            record.traceType,
            record.operator,
            record.operatorOrg,
            record.location,
            record.description,
            record.operationTime
        );
    }
    
    /**
     * 更新零部件状态
     */
    function updatePartStatus(
        string memory partNo,
        PartStatus newStatus
    ) public returns (bool) {
        require(partInfoMap[partNo].exists, "Part does not exist");
        
        PartStatus oldStatus = partInfoMap[partNo].status;
        partInfoMap[partNo].status = newStatus;
        
        // 触发事件
        emit PartStatusUpdated(partNo, oldStatus, newStatus);
        
        return true;
    }
    
    /**
     * 获取所有零部件编号
     */
    function getAllPartNos() public view returns (string[] memory) {
        return partNoList;
    }
    
    /**
     * 获取零部件总数
     */
    function getPartCount() public view returns (uint256) {
        return partNoList.length;
    }
}
```

## 5. 合约部署说明

### 5.1 编译合约

使用长安链提供的工具编译合约：
```bash
chainmaker contract compile PartsTraceContract.sol
```

### 5.2 部署合约

1. 通过长安链管理台部署
2. 或使用命令行工具部署：
```bash
chainmaker contract deploy \
  --contract-name PartsTraceContract \
  --contract-path ./PartsTraceContract.wasm \
  --runtime-type WASMER \
  --admin-keys ./admin.key \
  --admin-crts ./admin.crt
```

### 5.3 生成Java包装类

使用长安链工具生成Java包装类：
```bash
chainmaker contract generate-java \
  --contract-name PartsTraceContract \
  --output-dir ./src/main/java/com/kewei/springcloud/contract
```

## 6. 合约调用示例

### 6.1 Java调用示例

```java
// 加载合约
ChainClient chainClient = chainManager.getChainClient("chain1");
PartsTraceContract contract = PartsTraceContract.load(
    contractAddress, 
    chainClient, 
    user
);

// 注册零部件
TransactionResponse response = contract.registerPart(
    "PART001",
    "发动机活塞",
    "发动机部件",
    "XX制造有限公司",
    "MFG001",
    "2024-01-15",
    "BATCH20240115001",
    "{\"material\":\"铝合金\",\"weight\":\"500g\"}",
    "QmXXXXX"
);

// 查询零部件信息
PartInfo partInfo = contract.getPartInfo("PART001");

// 添加溯源记录
contract.addTraceRecord(
    "PART001",
    "质检",
    "张三",
    "OP001",
    "质检部门",
    "质检车间A",
    "质量检测合格"
);
```

## 7. 注意事项

1. **Gas费用**: 注意合约操作的Gas消耗，合理设计数据结构
2. **字符串长度**: Solidity中字符串操作成本较高，尽量控制字符串长度
3. **事件日志**: 合理使用事件记录重要操作，便于链下监听
4. **权限控制**: 根据业务需求添加权限控制逻辑
5. **错误处理**: 完善的错误处理和参数校验

## 8. 版本更新

### v1.0.0 (2024-01-15)
- 初始版本
- 实现基本的零部件注册和溯源功能
