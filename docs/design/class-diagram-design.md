# 零部件溯源系统 - 领域模型类图设计

## 1. 用户管理模块类图

### 1.1 核心实体类

```java
/**
 * 用户实体类
 */
@Entity
@Table(name = "sys_user")
public class SysUser {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String username;
    private String password;
    private String realName;
    private String email;
    private String phone;
    private String userType;  // normal, supply_chain, regulator
    private Integer status;
    private String avatar;
    private Date lastLoginTime;
    private String lastLoginIp;
    
    @OneToOne(mappedBy = "user")
    private SysUserDid userDid;
    
    @OneToMany(mappedBy = "user")
    private List<SysUserRsaKey> rsaKeys;
    
    @ManyToMany
    @JoinTable(name = "sys_user_role")
    private Set<SysRole> roles;
}

/**
 * 角色实体类
 */
@Entity
@Table(name = "sys_role")
public class SysRole {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String roleCode;
    private String roleName;
    private String description;
    private Integer sort;
    private Integer status;
    
    @ManyToMany(mappedBy = "roles")
    private Set<SysUser> users;
    
    @ManyToMany
    @JoinTable(name = "sys_role_permission")
    private Set<SysPermission> permissions;
}

/**
 * 权限实体类
 */
@Entity
@Table(name = "sys_permission")
public class SysPermission {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String permissionCode;
    private String permissionName;
    private String permissionType;  // menu, button, api
    private Long parentId;
    private String path;
    private String icon;
    private Integer sort;
    private Integer status;
    
    @ManyToMany(mappedBy = "permissions")
    private Set<SysRole> roles;
}

/**
 * 用户DID实体类
 */
@Entity
@Table(name = "sys_user_did")
public class SysUserDid {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @OneToOne
    @JoinColumn(name = "user_id")
    private SysUser user;
    
    private String did;
    private String didDocument;
    private String publicKey;
    private Integer bindStatus;
    private Date bindTime;
}

/**
 * 用户RSA密钥实体类
 */
@Entity
@Table(name = "sys_user_rsa_key")
public class SysUserRsaKey {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "user_id")
    private SysUser user;
    
    private String keyName;
    private String publicKey;
    private String privateKey;
    private String algorithm;
    private Integer keySize;
    private Integer isDefault;
    private Integer status;
    private Date expireTime;
}
```

### 1.2 服务层接口

```java
/**
 * 用户服务接口
 */
public interface UserService {
    // 用户注册
    void register(UserRegisterDTO registerDTO);
    
    // 用户登录
    UserLoginVO login(UserLoginDTO loginDTO);
    
    // 获取用户信息
    UserInfoVO getUserInfo(Long userId);
    
    // 更新用户信息
    void updateUserInfo(Long userId, UserInfoVO userInfoVO);
    
    // 修改密码
    void changePassword(Long userId, String oldPassword, String newPassword);
    
    // 启用/禁用用户
    void updateUserStatus(Long userId, Integer status);
    
    // 删除用户
    void deleteUser(Long userId);
}

/**
 * 角色服务接口
 */
public interface RoleService {
    // 获取所有角色
    List<SysRole> getAllRoles();
    
    // 创建角色
    void createRole(SysRole role);
    
    // 更新角色
    void updateRole(SysRole role);
    
    // 删除角色
    void deleteRole(Long roleId);
    
    // 为用户分配角色
    void assignRoleToUser(Long userId, Long roleId);
    
    // 移除用户角色
    void removeRoleFromUser(Long userId, Long roleId);
    
    // 获取用户的所有角色
    List<SysRole> getUserRoles(Long userId);
}

/**
 * DID服务接口
 */
public interface DidService {
    // 绑定DID
    SysUserDid bindDid(Long userId, DidBindDTO bindDTO);
    
    // 解绑DID
    void unbindDid(Long userId);
    
    // 获取用户DID信息
    SysUserDid getDidByUserId(Long userId);
    
    // 验证DID
    boolean verifyDid(String did, String signature);
}

/**
 * RSA密钥服务接口
 */
public interface RsaKeyService {
    // 生成RSA密钥对
    SysUserRsaKey generateKeyPair(RsaKeyGenerateDTO generateDTO);
    
    // 获取用户的所有密钥
    List<SysUserRsaKey> getKeysByUserId(Long userId);
    
    // 获取用户的默认密钥
    SysUserRsaKey getDefaultKeyByUserId(Long userId);
    
    // 设置默认密钥
    void setDefaultKey(Long userId, Long keyId);
    
    // 删除密钥
    void deleteKey(Long userId, Long keyId);
    
    // 更新密钥状态
    void updateKeyStatus(Long userId, Long keyId, Integer status);
}
```

## 2. 零部件管理模块类图

### 2.1 核心实体类

```java
/**
 * 零部件分类实体类
 */
@Entity
@Table(name = "parts_category")
public class PartsCategory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String categoryCode;
    private String categoryName;
    private Long parentId;
    private Integer level;
    private Integer sort;
    private String icon;
    private String description;
    private Integer status;
    
    @OneToMany(mappedBy = "category")
    private List<PartsInfo> partsList;
}

/**
 * 零部件基础信息实体类
 */
@Entity
@Table(name = "parts_info")
public class PartsInfo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String partsCode;
    private String partsName;
    private String batchNumber;
    
    @ManyToOne
    @JoinColumn(name = "category_id")
    private PartsCategory category;
    
    private String specification;
    private String unit;
    private BigDecimal quantity;
    private BigDecimal weight;
    private String material;
    private String manufacturer;
    private Long manufacturerId;
    private Date productionDate;
    private Date expiryDate;
    private String qualityStandard;
    private String certification;
    private String imageUrl;
    private String description;
    private Integer status;
    private Long creatorId;
    
    @OneToOne(mappedBy = "parts")
    private PartsDid partsDid;
    
    @OneToMany(mappedBy = "parts")
    private List<PartsSpecification> specifications;
    
    @OneToMany(mappedBy = "parts")
    private List<TraceEvent> traceEvents;
    
    @OneToMany(mappedBy = "parts")
    private List<BlockchainRecord> blockchainRecords;
}

/**
 * 零部件DID实体类
 */
@Entity
@Table(name = "parts_did")
public class PartsDid {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @OneToOne
    @JoinColumn(name = "parts_id")
    private PartsInfo parts;
    
    private String did;
    private String didDocument;
    private String publicKey;
    private Integer bindStatus;
    private Date bindTime;
}

/**
 * 零部件规格实体类
 */
@Entity
@Table(name = "parts_specification")
public class PartsSpecification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "parts_id")
    private PartsInfo parts;
    
    private String specKey;
    private String specValue;
    private String specUnit;
    private Integer sort;
}

/**
 * 区块链上链记录实体类
 */
@Entity
@Table(name = "blockchain_record")
public class BlockchainRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private Long businessId;
    private String businessType;  // parts, event, credential
    private String chainType;
    private String txId;
    private Long blockHeight;
    private String blockHash;
    private String contractName;
    private String contractMethod;
    private String dataHash;
    private String chainData;
    private Integer status;
    private String errorMsg;
    private Integer retryCount;
    private Long creatorId;
}
```

### 2.2 服务层接口

```java
/**
 * 零部件服务接口
 */
public interface PartsService {
    // 创建零部件
    PartsInfo createParts(PartsCreateDTO createDTO);
    
    // 更新零部件信息
    void updateParts(Long partsId, PartsUpdateDTO updateDTO);
    
    // 获取零部件详情
    PartsInfoVO getPartsInfo(Long partsId);
    
    // 根据批次号查询零部件
    PartsInfo getPartsByBatchNumber(String batchNumber);
    
    // 分页查询零部件列表
    Page<PartsInfoVO> getPartsList(PartsQueryDTO queryDTO);
    
    // 删除零部件
    void deleteParts(Long partsId);
    
    // 归档零部件
    void archiveParts(Long partsId);
    
    // 生成零部件DID
    PartsDid generatePartsDid(Long partsId);
}

/**
 * 零部件分类服务接口
 */
public interface PartsCategoryService {
    // 获取分类树
    List<PartsCategoryTreeVO> getCategoryTree();
    
    // 创建分类
    void createCategory(PartsCategory category);
    
    // 更新分类
    void updateCategory(PartsCategory category);
    
    // 删除分类
    void deleteCategory(Long categoryId);
}

/**
 * 区块链服务接口
 */
public interface BlockchainService {
    // 上链
    BlockchainRecord uploadToChain(Long businessId, String businessType, String data);
    
    // 查询上链记录
    BlockchainRecord getRecordByTxId(String txId);
    
    // 验证上链数据
    boolean verifyChainData(String txId, String dataHash);
    
    // 重试上链
    void retryUpload(Long recordId);
}
```

## 3. 零部件溯源模块类图

### 3.1 核心实体类

```java
/**
 * 溯源事件实体类（主表）
 */
@Entity
@Table(name = "trace_event")
public class TraceEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String eventCode;
    
    @ManyToOne
    @JoinColumn(name = "parts_id")
    private PartsInfo parts;
    
    private String eventType;  // production, quality, logistics
    private String eventName;
    private Date eventTime;
    private String eventLocation;
    private Long operatorId;
    private String operatorName;
    private String organization;
    private String eventData;
    private String evidenceUrls;
    
    @ManyToOne
    @JoinColumn(name = "previous_event_id")
    private TraceEvent previousEvent;
    
    @OneToOne(mappedBy = "previousEvent")
    private TraceEvent nextEvent;
    
    private String blockchainTxId;
    private Integer status;
    private String remark;
    
    @OneToOne(mappedBy = "event")
    private TraceProduction productionDetail;
    
    @OneToOne(mappedBy = "event")
    private TraceQuality qualityDetail;
    
    @OneToOne(mappedBy = "event")
    private TraceLogistics logisticsDetail;
    
    @OneToMany(mappedBy = "event")
    private List<VerifiableCredential> credentials;
}

/**
 * 生产事件实体类
 */
@Entity
@Table(name = "trace_production")
public class TraceProduction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @OneToOne
    @JoinColumn(name = "event_id")
    private TraceEvent event;
    
    @ManyToOne
    @JoinColumn(name = "parts_id")
    private PartsInfo parts;
    
    private String productionLine;
    private String productionBatch;
    private BigDecimal productionQuantity;
    private String rawMaterials;
    private String productionProcess;
    private String equipmentInfo;
    private BigDecimal temperature;
    private BigDecimal humidity;
    private BigDecimal pressure;
    private String qualityLevel;
    private String inspector;
    private String inspectionResult;
}

/**
 * 质检事件实体类
 */
@Entity
@Table(name = "trace_quality")
public class TraceQuality {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @OneToOne
    @JoinColumn(name = "event_id")
    private TraceEvent event;
    
    @ManyToOne
    @JoinColumn(name = "parts_id")
    private PartsInfo parts;
    
    private String inspectionType;  // incoming, process, final
    private String inspectionStandard;
    private String inspectionItems;
    private String inspectionResult;  // pass, fail, pending
    private BigDecimal qualifiedQuantity;
    private BigDecimal unqualifiedQuantity;
    private String defectDescription;
    private String inspector;
    private Long inspectorId;
    private String inspectionEquipment;
    private String testData;
    private String reportNumber;
    private String reportUrl;
}

/**
 * 物流事件实体类
 */
@Entity
@Table(name = "trace_logistics")
public class TraceLogistics {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @OneToOne
    @JoinColumn(name = "event_id")
    private TraceEvent event;
    
    @ManyToOne
    @JoinColumn(name = "parts_id")
    private PartsInfo parts;
    
    private String logisticsType;  // shipping, receiving, transit
    private String logisticsCompany;
    private String trackingNumber;
    private String sender;
    private String senderAddress;
    private String receiver;
    private String receiverAddress;
    private Date departureTime;
    private Date arrivalTime;
    private String currentLocation;
    private String transportMode;  // road, rail, air, sea
    private String vehicleNumber;
    private String driver;
    private String driverPhone;
    private String temperatureRecord;
    private String humidityRecord;
    private String gpsTrack;
    private String packageStatus;
    private String signatureUrl;
}

/**
 * 可验证凭证实体类（VC）
 */
@Entity
@Table(name = "verifiable_credential")
public class VerifiableCredential {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String vcId;
    
    @ManyToOne
    @JoinColumn(name = "parts_id")
    private PartsInfo parts;
    
    @ManyToOne
    @JoinColumn(name = "event_id")
    private TraceEvent event;
    
    private String vcType;  // production, quality, transfer, comprehensive
    private String issuerDid;
    private String issuerName;
    private String subjectDid;
    private Date issuanceDate;
    private Date expirationDate;
    private String credentialData;
    private String proof;
    private Integer status;
    private String revocationReason;
    private String blockchainTxId;
    
    @OneToOne(mappedBy = "credential")
    private ProductionCertificate productionCertificate;
    
    @OneToOne(mappedBy = "credential")
    private QualityReport qualityReport;
    
    @OneToOne(mappedBy = "credential")
    private TransferCertificate transferCertificate;
}
```

### 3.2 服务层接口

```java
/**
 * 溯源事件服务接口
 */
public interface TraceEventService {
    // 记录生产事件
    TraceEvent recordProductionEvent(ProductionEventDTO eventDTO);
    
    // 记录质检事件
    TraceEvent recordQualityEvent(QualityEventDTO eventDTO);
    
    // 记录物流事件
    TraceEvent recordLogisticsEvent(LogisticsEventDTO eventDTO);
    
    // 获取零部件的所有事件
    List<TraceEvent> getPartEvents(Long partsId);
    
    // 获取溯源链条
    List<TraceEvent> getTraceChain(Long partsId);
    
    // 获取事件详情
    TraceEventDetailVO getEventDetail(Long eventId);
}

/**
 * 可验证凭证服务接口
 */
public interface VerifiableCredentialService {
    // 签发生产证明
    VerifiableCredential issueProductionCertificate(Long eventId);
    
    // 签发质检报告
    VerifiableCredential issueQualityReport(Long eventId);
    
    // 签发流转证明
    VerifiableCredential issueTransferCertificate(Long eventId);
    
    // 签发综合溯源凭证
    VerifiableCredential issueComprehensiveCredential(Long partsId);
    
    // 验证凭证
    boolean verifyCredential(String vcId);
    
    // 撤销凭证
    void revokeCredential(String vcId, String reason);
    
    // 获取零部件的所有凭证
    List<VerifiableCredential> getPartsCredentials(Long partsId);
}
```

## 4. 类图关系说明

### 4.1 继承关系
- 无明显继承关系（采用组合优于继承原则）

### 4.2 关联关系
- **一对一**: User ↔ UserDid, Parts ↔ PartsDid, TraceEvent ↔ TraceProduction/Quality/Logistics
- **一对多**: User → RsaKey, Parts → TraceEvent, TraceEvent → VerifiableCredential
- **多对多**: User ↔ Role, Role ↔ Permission

### 4.3 依赖关系
- Controller → Service → Repository
- Service → Util (JwtUtil, RsaUtil, DidUtil)
- Service → DTO/VO

### 4.4 聚合关系
- PartsInfo 聚合 PartsSpecification
- TraceEvent 聚合 TraceProduction/Quality/Logistics

## 5. 设计模式应用

### 5.1 工厂模式
```java
public class TraceEventFactory {
    public static TraceEvent createEvent(String eventType, EventDTO dto) {
        switch (eventType) {
            case "production":
                return createProductionEvent(dto);
            case "quality":
                return createQualityEvent(dto);
            case "logistics":
                return createLogisticsEvent(dto);
            default:
                throw new IllegalArgumentException("Unknown event type");
        }
    }
}
```

### 5.2 策略模式
```java
public interface CredentialIssueStrategy {
    VerifiableCredential issue(TraceEvent event);
}

public class ProductionCertificateStrategy implements CredentialIssueStrategy {
    @Override
    public VerifiableCredential issue(TraceEvent event) {
        // 签发生产证明逻辑
    }
}
```

### 5.3 观察者模式
```java
public interface EventListener {
    void onEventRecorded(TraceEvent event);
}

public class BlockchainUploadListener implements EventListener {
    @Override
    public void onEventRecorded(TraceEvent event) {
        // 自动上链
    }
}
```

### 5.4 责任链模式
```java
public abstract class ValidationHandler {
    protected ValidationHandler next;
    
    public void setNext(ValidationHandler handler) {
        this.next = handler;
    }
    
    public abstract boolean validate(Object data);
}
```
