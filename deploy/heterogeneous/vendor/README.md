# 官方发行文件

`fisco-build-chain.sh` 来自 FISCO BCOS 3.8.0 官方发行页，未修改。

- 下载地址：https://github.com/FISCO-BCOS/FISCO-BCOS/releases/download/v3.8.0/build_chain.sh
- SHA256：`18e7686716821d4a52273069ed70b9cbb43f3c161ddd632414bb9a88862e6e91`

节点发行包 `fisco-bcos-linux-x86_64-v3.16.3.tar.gz` 从 3.16.3 官方发行页下载，用于构建本项目的节点镜像。发行包不提交到源码仓库。

- 下载地址：https://github.com/FISCO-BCOS/FISCO-BCOS/releases/download/v3.16.3/fisco-bcos-linux-x86_64.tar.gz
- SHA256：`1d97a6d72a1e384b428fdefac77ff48ffbe36d21eb9b4daeba62cf56bf975ae3`

上述哈希记录本次下载文件的内容，供后续构建核验。上游项目采用 Apache License 2.0，许可证见本目录的 `FISCO-BCOS-LICENSE`。

网络沿用官方 3.8.0 建链脚本生成的证书和 3.8.0 兼容配置，节点二进制使用 3.16.3，Java SDK 为 3.8.0。原 3.8.0 节点在本机实测中出现重启后查询合约退出的情况，改用新版二进制，已有创世配置、私钥和账本均保留。
