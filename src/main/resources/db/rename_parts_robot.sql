-- =============================================================================
-- 零部件名称全量更新：汽车配件 → 机器人零部件溯源主题
-- 用法：mysql -u root -p parts_trace < rename_parts_robot.sql
-- 说明：保留 parts_code / batch_number 不变，仅更新名称及关联描述字段
-- =============================================================================

USE parts_trace;

-- 分类名称
UPDATE parts_category SET category_name = '驱动系统'      WHERE category_code = 'ENGINE';
UPDATE parts_category SET category_name = '运动结构'      WHERE category_code = 'CHASSIS';
UPDATE parts_category SET category_name = '感知与控制'    WHERE category_code = 'ELECTRIC';
UPDATE parts_category SET category_name = '外壳与防护'    WHERE category_code = 'BODY';
UPDATE parts_category SET category_name = '核心驱动'      WHERE category_code = 'ENGINE_CORE';
UPDATE parts_category SET category_name = '动力传动'      WHERE category_code = 'ENGINE_FUEL';
UPDATE parts_category SET category_name = '制动与安全'    WHERE category_code = 'CHASSIS_BRAKE';
UPDATE parts_category SET category_name = '缓冲与减振'    WHERE category_code = 'CHASSIS_SUSP';
UPDATE parts_category SET category_name = '散热系统'      WHERE category_code = 'ENGINE_COOL';
UPDATE parts_category SET category_name = '排热与防护'    WHERE category_code = 'ENGINE_EXHAUST';
UPDATE parts_category SET category_name = '转向与关节'    WHERE category_code = 'CHASSIS_STEER';
UPDATE parts_category SET category_name = '传动联接'      WHERE category_code = 'CHASSIS_TRANS';
UPDATE parts_category SET category_name = '传感模块'      WHERE category_code = 'ELECTRIC_SENSOR';
UPDATE parts_category SET category_name = '控制单元'      WHERE category_code = 'ELECTRIC_ECU';
UPDATE parts_category SET category_name = '壳体结构'      WHERE category_code = 'BODY_DOOR';
UPDATE parts_category SET category_name = '视窗与防护'    WHERE category_code = 'BODY_GLASS';
UPDATE parts_category SET category_name = '伺服转子组件'  WHERE category_code = 'ENGINE_CORE_PISTON';
UPDATE parts_category SET category_name = '关节轴组件'    WHERE category_code = 'ENGINE_CORE_CRANK';
UPDATE parts_category SET category_name = '精密传动件'    WHERE category_code = 'ENGINE_FUEL_INJ';
UPDATE parts_category SET category_name = '关节制动盘'    WHERE category_code = 'CHASSIS_BRAKE_DISC';
UPDATE parts_category SET category_name = '电磁制动片'    WHERE category_code = 'CHASSIS_BRAKE_PAD';
UPDATE parts_category SET category_name = '关节缓冲器'    WHERE category_code = 'CHASSIS_SUSP_SPRING';

-- 零部件主数据（按 parts_code 对齐 init.sql）
UPDATE parts_info SET parts_name = '谐波减速器模组',     specification = '减速比：100:1，额定扭矩：120N·m',           material = '合金钢+柔轮',   manufacturer = '新松机器人自动化股份有限公司',       quality_standard = 'GB/T 34884', description = '六轴协作机器人关节核心减速部件'         WHERE parts_code = 'PT-ENG-001';
UPDATE parts_info SET parts_name = '伺服电机定子组件',   specification = '额定功率：400W，峰值转速：3000rpm',         material = '硅钢片+铜线',   manufacturer = '埃斯顿自动化股份有限公司',             quality_standard = 'GB/T 12668', description = '适用于轻载协作机械臂关节驱动'           WHERE parts_code = 'PT-ENG-002';
UPDATE parts_info SET parts_name = '六轴机械臂关节轴',   specification = '材质：40Cr，同轴度≤0.02mm',                 material = '40Cr合金钢',    manufacturer = '节卡机器人（上海）有限公司',           quality_standard = 'GB/T 1804',  description = '六轴工业机器人旋转关节主轴'             WHERE parts_code = 'PT-ENG-003';
UPDATE parts_info SET parts_name = '行星减速器总成',     specification = '减速比：10:1，背隙≤8arcmin',                material = '合金钢',        manufacturer = '汇川技术股份有限公司',                 quality_standard = 'JB/T 9050',  description = 'SCARA 机器人小臂关节传动模块'           WHERE parts_code = 'PT-ENG-004';
UPDATE parts_info SET parts_name = '协作机器人腕部总成', specification = '自由度：2DOF，重复定位精度：±0.02mm',      material = '铝合金+轴承钢', manufacturer = '遨博（北京）智能科技股份有限公司',     quality_standard = 'ISO 9283',   description = '协作机器人末端腕部旋转模块'             WHERE parts_code = 'PT-ENG-005';
UPDATE parts_info SET parts_name = '移动底盘驱动轮',     specification = '直径：200mm，额定负载：50kg',               material = '聚氨酯+铝合金', manufacturer = '极智嘉（Geek+）科技股份有限公司',      quality_standard = 'GB/T 276',    description = 'AMR 仓储机器人底盘驱动轮'               WHERE parts_code = 'PT-CHN-001';
UPDATE parts_info SET parts_name = '关节电磁制动片组',   specification = '保持扭矩：5N·m，响应时间≤50ms',             material = '摩擦复合材料',  manufacturer = '配天机器人技术有限公司',               quality_standard = 'GB/T 5763',  description = '伺服关节断电保持制动摩擦片'             WHERE parts_code = 'PT-CHN-002';
UPDATE parts_info SET parts_name = '机械臂关节缓冲器',   specification = '行程：15mm，阻尼系数可调',                  material = '高强度钢',      manufacturer = '珞石（山东）智能科技有限公司',         quality_standard = 'GB/T 1972',  description = '关节碰撞保护与减振缓冲模块'             WHERE parts_code = 'PT-CHN-003';
UPDATE parts_info SET parts_name = '末端夹爪旋转接头',   specification = '旋转角度：±180°，额定负载：3kg',            material = '40Cr钢',        manufacturer = '大族机器人有限公司',                   quality_standard = 'GB/T 3091',  description = '二指平行夹爪万向旋转接头'             WHERE parts_code = 'PT-CHN-004';
UPDATE parts_info SET parts_name = '机器人传动同步轮组', specification = '节距：5mm，传动比：2:1',                    material = '铝合金+PU',     manufacturer = '翼菲智能科技（上海）有限公司',         quality_standard = 'GB/T 11357', description = 'Delta 并联机器人传动同步轮组'           WHERE parts_code = 'PT-CHN-005';
UPDATE parts_info SET parts_name = '机器人运动控制器',   specification = 'EtherCAT/CANopen，8轴控制',                 material = 'PCB+铝合金外壳',manufacturer = '卡诺普机器人股份有限公司',           quality_standard = 'GB/T 12668', description = '多轴工业机器人实时运动控制主站'         WHERE parts_code = 'PT-ELE-001';
UPDATE parts_info SET parts_name = '六维力矩传感器',     specification = '量程：200N/10N·m，精度：0.5%FS',            material = '合金钢+应变片', manufacturer = 'ATI Industrial Automation（中国）',    quality_standard = 'ISO 9001',   description = '装配机器人末端力控与柔顺装配传感'       WHERE parts_code = 'PT-ELE-002';
UPDATE parts_info SET parts_name = '关节温度传感探头',   specification = '测量范围：-20~150℃，精度±0.5℃',           material = '铂电阻',        manufacturer = '汉威科技集团股份有限公司',             quality_standard = 'GB/T 30121', description = '伺服关节过热监测与保护传感'             WHERE parts_code = 'PT-ELE-003';
UPDATE parts_info SET parts_name = '协作机器人前臂壳体', specification = '尺寸：380×220mm，材质：碳纤维复材',         material = '碳纤维复合材料',manufacturer = '越疆科技股份有限公司',                 quality_standard = 'GB/T 1446',  description = '含内骨架、线缆通道与标识区'             WHERE parts_code = 'PT-BDY-001';
UPDATE parts_info SET parts_name = '安全光幕防护罩',     specification = '防护高度：1200mm，响应时间≤14ms',           material = '聚碳酸酯+铝框', manufacturer = '皮尔磁（Pilz）中国有限公司',           quality_standard = 'GB/T 19876', description = '人机协作工位安全隔离与光幕防护'         WHERE parts_code = 'PT-BDY-002';
UPDATE parts_info SET parts_name = 'AGV 激光导航模组',   specification = '扫描角：270°，定位精度：±10mm',             material = '光学玻璃+PCB',  manufacturer = '仙工智能（SEER）科技有限公司',         quality_standard = 'GB/T 38124', description = '移动机器人 SLAM 激光导航核心模组'       WHERE parts_code = 'PT-ENG-006';
UPDATE parts_info SET parts_name = '伺服驱动散热单元',   specification = '风量：12CFM，温升控制≤25K',                 material = '铝合金',        manufacturer = '绿的谐波传动科技股份有限公司',         quality_standard = 'GB/T 17799', description = '关节伺服驱动器散热与风道模块'           WHERE parts_code = 'PT-ENG-007';
UPDATE parts_info SET parts_name = '并联机器人支腿连杆', specification = '长度：320mm，额定载荷：15kg',                 material = '45#钢',         manufacturer = '勃肯特（宁波）机器人技术有限公司',     quality_standard = 'GB/T 3098',  description = 'Delta 高速并联机器人支腿连杆'           WHERE parts_code = 'PT-CHN-006';
UPDATE parts_info SET parts_name = '超声避障传感器',     specification = '探测距离：0.05~4m，频率：40kHz',            material = 'ABS+铝壳',      manufacturer = '思岚科技（上海）有限公司',             quality_standard = 'ISO 9001',   description = '服务机器人与 AGV 近距离避障探头'         WHERE parts_code = 'PT-ELE-004';
UPDATE parts_info SET parts_name = '示教器手持外壳',     specification = '7寸触摸屏，IP54防护',                        material = 'ABS+硅胶',      manufacturer = '华数机器人股份有限公司',               quality_standard = 'GB/T 4208',  description = '工业机器人现场示教与编程手持终端外壳'   WHERE parts_code = 'PT-BDY-003';

SELECT parts_code, parts_name, manufacturer FROM parts_info ORDER BY id;
