// SPDX-License-Identifier: MIT
pragma solidity ^0.8.11;

/** RFC 8017 RSASSA-PKCS1-v1_5，SHA-256 DigestInfo使用显式NULL参数。
 * 只接受2048至4096位模数和65537指数，调用EIP-198模幂预编译。
 * Java与Go签名向量、错误填充及篡改反例见测试。
 */
library RsaSha256 {
    function verify(bytes32 digest, bytes memory signature, bytes memory modulus, bytes memory exponent)
        internal view returns (bool)
    {
        uint256 size = modulus.length;
        if (size < 256 || size > 512 || signature.length != size || exponent.length != 3)
            return false;
        if (exponent[0] != 0x01 || exponent[1] != 0x00 || exponent[2] != 0x01
            || uint8(modulus[0]) < 128 || uint8(modulus[size - 1]) % 2 == 0) return false;

        // RSAVP1要求0 <= s < n，不能仅依赖模幂对越界签名取模。
        bool smaller;
        for (uint256 i; i < size; i++) {
            if (signature[i] < modulus[i]) { smaller = true; break; }
            if (signature[i] > modulus[i]) return false;
        }
        if (!smaller) return false;

        bytes memory input = abi.encodePacked(uint256(size), uint256(exponent.length), uint256(size), signature, exponent, modulus);
        (bool success, bytes memory encoded) = address(5).staticcall(input);
        if (!success || encoded.length != size || encoded[0] != 0x00 || encoded[1] != 0x01) return false;
        bytes memory prefix = hex"3031300d060960864801650304020105000420";
        uint256 separator = size - prefix.length - 32 - 1;
        if (separator < 10 || encoded[separator] != 0x00) return false;
        for (uint256 i = 2; i < separator; i++) if (encoded[i] != 0xff) return false;
        for (uint256 i; i < prefix.length; i++) if (encoded[separator + 1 + i] != prefix[i]) return false;
        for (uint256 i; i < 32; i++) if (encoded[size - 32 + i] != digest[i]) return false;
        return true;
    }
}
