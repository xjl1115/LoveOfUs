/**
 * 对密码进行 SHA-256 哈希加密
 * 纯 JS 实现（FIPS-180-4），不依赖 crypto.subtle
 * 在发送到后端前对密码进行加密，确保传输安全
 */

export async function hashPassword(password: string): Promise<string> {
  return sha256(password)
}

function sha256(msg: string): string {
  // UTF-8 编码
  const utf8 = unescape(encodeURIComponent(msg))

  // 转为字节数组
  const m: number[] = []
  for (let i = 0; i < utf8.length; i++) {
    m.push(utf8.charCodeAt(i))
  }

  // 原始消息长度（位）
  const originalBitLen = m.length * 8

  // 1. 追加 0x80
  m.push(0x80)

  // 2. 填充 0 直到长度 ≡ 448 (mod 512)
  while ((m.length * 8) % 512 !== 448) {
    m.push(0)
  }

  // 3. 追加 64 位原始长度（大端序）
  // 高 32 位（短消息全为 0）
  m.push(0); m.push(0); m.push(0); m.push(0)
  // 低 32 位
  m.push((originalBitLen >>> 24) & 0xff)
  m.push((originalBitLen >>> 16) & 0xff)
  m.push((originalBitLen >>> 8) & 0xff)
  m.push(originalBitLen & 0xff)

  // 循环右移
  const rightRotate = (n: number, b: number): number =>
    (n >>> b) | (n << (32 - b))

  // 常量 K（前 64 个质数的立方根小数部分的前 32 位）
  const K = [
    0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5,
    0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
    0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3,
    0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
    0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc,
    0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
    0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7,
    0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
    0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13,
    0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
    0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3,
    0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
    0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5,
    0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
    0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208,
    0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2
  ]

  // 初始哈希值（前 8 个质数的平方根小数部分的前 32 位）
  let H0 = 0x6a09e667, H1 = 0xbb67ae85
  let H2 = 0x3c6ef372, H3 = 0xa54ff53a
  let H4 = 0x510e527f, H5 = 0x9b05688c
  let H6 = 0x1f83d9ab, H7 = 0x5be0cd19

  // 处理每个 512 位（64 字节）分组
  for (let chunkStart = 0; chunkStart < m.length; chunkStart += 64) {
    const w: number[] = new Array(64)

    // 前 16 个字直接来自消息
    for (let t = 0; t < 16; t++) {
      const i = chunkStart + t * 4
      w[t] = (m[i] << 24) | (m[i + 1] << 16) | (m[i + 2] << 8) | m[i + 3]
    }

    // 扩展到 64 个字
    for (let t = 16; t < 64; t++) {
      const s0 = rightRotate(w[t - 15], 7) ^ rightRotate(w[t - 15], 18) ^ (w[t - 15] >>> 3)
      const s1 = rightRotate(w[t - 2], 17) ^ rightRotate(w[t - 2], 19) ^ (w[t - 2] >>> 10)
      w[t] = (w[t - 16] + s0 + w[t - 7] + s1) >>> 0
    }

    let a = H0, b = H1, c = H2, d = H3
    let e = H4, f = H5, g = H6, h = H7

    // 压缩主循环
    for (let t = 0; t < 64; t++) {
      const S1 = rightRotate(e, 6) ^ rightRotate(e, 11) ^ rightRotate(e, 25)
      const ch = (e & f) ^ ((~e) & g)
      const temp1 = (h + S1 + ch + K[t] + w[t]) >>> 0
      const S0 = rightRotate(a, 2) ^ rightRotate(a, 13) ^ rightRotate(a, 22)
      const maj = (a & b) ^ (a & c) ^ (b & c)
      const temp2 = (S0 + maj) >>> 0

      h = g; g = f; f = e
      e = (d + temp1) >>> 0
      d = c; c = b; b = a
      a = (temp1 + temp2) >>> 0
    }

    H0 = (H0 + a) >>> 0; H1 = (H1 + b) >>> 0
    H2 = (H2 + c) >>> 0; H3 = (H3 + d) >>> 0
    H4 = (H4 + e) >>> 0; H5 = (H5 + f) >>> 0
    H6 = (H6 + g) >>> 0; H7 = (H7 + h) >>> 0
  }

  // 输出十六进制字符串
  const toHex = (n: number): string =>
    n.toString(16).padStart(8, '0')

  return toHex(H0) + toHex(H1) + toHex(H2) + toHex(H3) +
         toHex(H4) + toHex(H5) + toHex(H6) + toHex(H7)
}