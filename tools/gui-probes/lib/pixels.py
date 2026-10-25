"""纯标准库的 PNG 编解码与 pixelmatch 5.3.0 像素比对。

用途：GUI 探针比对两次真实截图，给出差异像素数、比例和可直接嵌进报告的 diff 图，
不依赖 Pillow 或外部服务。

边界：
- decode_png 只支持 8 bit、非隔行的灰度 / 灰度+alpha / 真彩 / 真彩+alpha PNG；
  索引色（color type 3）、16 bit、隔行 PNG 明确报错。
- compare 要求两张图同宽高，语义与 pixelmatch 5.3.0 一致：threshold 是 YIQ 感知距离
  阈值；include_aa=False 时抗锯齿差异记为忽略（diff 图上标黄）且不计入 diff_pixels。
- 逐像素 Python 循环，1920x1080 量级约需数秒；批量比对时自行控制规模。
"""

import struct
import zlib
from collections.abc import Iterator
from typing import TypedDict

RGBA = bytes | bytearray

_CHANNELS = {0: 1, 2: 3, 4: 2, 6: 4}
_DIFF_COLOR = bytes((255, 0, 0, 255))
_ANTIALIAS_COLOR = bytes((255, 255, 0, 255))


class CompareResult(TypedDict):
    """compare 的返回结构；diff_png 是可写入文件或 base64 内嵌的 PNG 字节。"""

    diff_pixels: int
    total_pixels: int
    ratio: float
    diff_png: bytes


def _chunks(blob: bytes) -> Iterator[tuple[bytes, bytes]]:
    """按 PNG 规范逐块产出 (类型, 数据)。"""
    pos = 8
    while pos + 8 <= len(blob):
        (length,) = struct.unpack(">I", blob[pos:pos + 4])
        tag = blob[pos + 4:pos + 8]
        body = blob[pos + 8:pos + 8 + length]
        pos += 12 + length
        yield tag, body


def decode_png(blob: bytes) -> tuple[int, int, bytearray]:
    """把 PNG 字节解码为 (宽, 高, RGBA 字节序列)，alpha 缺失时补 255。"""
    width = height = bit_depth = color_type = interlace = 0
    idat: list[bytes] = []
    for tag, body in _chunks(blob):
        if tag == b"IHDR":
            width, height, bit_depth, color_type, _comp, _filt, interlace = struct.unpack(">IIBBBBB", body)
        elif tag == b"IDAT":
            idat.append(body)
        elif tag == b"IEND":
            break
    if interlace:
        raise ValueError("不支持隔行 PNG")
    if bit_depth != 8:
        raise ValueError(f"不支持 {bit_depth} bit 的 PNG，仅支持 8 bit")
    channels = _CHANNELS.get(color_type)
    if channels is None:
        raise ValueError(f"不支持 PNG color type {color_type}，仅支持灰度/真彩及其 alpha 变体")

    raw = zlib.decompress(b"".join(idat))
    stride = width * channels
    out = bytearray(width * height * 4)
    prev = bytearray(stride)
    pos = 0
    for y in range(height):
        filter_type = raw[pos]
        pos += 1
        line = bytearray(raw[pos:pos + stride])
        pos += stride
        if filter_type == 1:
            for i in range(channels, stride):
                line[i] = (line[i] + line[i - channels]) & 255
        elif filter_type == 2:
            for i in range(stride):
                line[i] = (line[i] + prev[i]) & 255
        elif filter_type == 3:
            for i in range(stride):
                left = line[i - channels] if i >= channels else 0
                line[i] = (line[i] + ((left + prev[i]) >> 1)) & 255
        elif filter_type == 4:
            for i in range(stride):
                a = line[i - channels] if i >= channels else 0
                b = prev[i]
                c = prev[i - channels] if i >= channels else 0
                p = a + b - c
                pa = abs(p - a)
                pb = abs(p - b)
                pc = abs(p - c)
                predictor = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                line[i] = (line[i] + predictor) & 255
        base = y * width * 4
        if channels == 4:
            out[base:base + width * 4] = line
        elif channels == 3:
            for x in range(width):
                out[base + x * 4] = line[x * 3]
                out[base + x * 4 + 1] = line[x * 3 + 1]
                out[base + x * 4 + 2] = line[x * 3 + 2]
                out[base + x * 4 + 3] = 255
        elif channels == 1:
            for x in range(width):
                value = line[x]
                out[base + x * 4] = out[base + x * 4 + 1] = out[base + x * 4 + 2] = value
                out[base + x * 4 + 3] = 255
        else:
            for x in range(width):
                value = line[x * 2]
                out[base + x * 4] = out[base + x * 4 + 1] = out[base + x * 4 + 2] = value
                out[base + x * 4 + 3] = line[x * 2 + 1]
        prev = line
    return width, height, out


def encode_png(width: int, height: int, rgba: RGBA) -> bytes:
    """把 RGBA 字节序列编码成 8 bit 真彩+alpha 的 PNG 字节。"""
    if width <= 0 or height <= 0:
        raise ValueError("宽高必须为正数")
    if len(rgba) != width * height * 4:
        raise ValueError("RGBA 字节数与宽高不匹配")

    raw = bytearray()
    stride = width * 4
    for y in range(height):
        raw.append(0)
        raw.extend(rgba[y * stride:(y + 1) * stride])

    def chunk(tag: bytes, data: bytes) -> bytes:
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    ihdr = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr)
            + chunk(b"IDAT", zlib.compress(bytes(raw), 6)) + chunk(b"IEND", b""))


def _rgb2y(red: float, green: float, blue: float) -> float:
    return red * 0.29889531 + green * 0.58662247 + blue * 0.11448223


def _rgb2i(red: float, green: float, blue: float) -> float:
    return red * 0.59597799 - green * 0.27417610 - blue * 0.32180189


def _rgb2q(red: float, green: float, blue: float) -> float:
    return red * 0.21147017 - green * 0.52261711 + blue * 0.31114694


def _blend(channel: float, alpha: float) -> float:
    return 255 + (channel - 255) * alpha


def _color_delta(img1: RGBA, img2: RGBA, k: int, m: int, y_only: bool = False) -> float:
    """pixelmatch 的 YIQ 颜色距离；y_only 时只回亮度差。"""
    r1, g1, b1, a1 = img1[k], img1[k + 1], img1[k + 2], img1[k + 3]
    r2, g2, b2, a2 = img2[m], img2[m + 1], img2[m + 2], img2[m + 3]
    if a1 == a2 and r1 == r2 and g1 == g2 and b1 == b2:
        return 0.0
    if a1 < 255:
        alpha = a1 / 255
        r1, g1, b1 = _blend(r1, alpha), _blend(g1, alpha), _blend(b1, alpha)
    if a2 < 255:
        alpha = a2 / 255
        r2, g2, b2 = _blend(r2, alpha), _blend(g2, alpha), _blend(b2, alpha)
    y1 = _rgb2y(r1, g1, b1)
    y2 = _rgb2y(r2, g2, b2)
    y = y1 - y2
    if y_only:
        return y
    i = _rgb2i(r1, g1, b1) - _rgb2i(r2, g2, b2)
    q = _rgb2q(r1, g1, b1) - _rgb2q(r2, g2, b2)
    delta = 0.5053 * y * y + 0.299 * i * i + 0.1957 * q * q
    return -delta if y1 > y2 else delta


def _has_many_siblings(img: RGBA, x1: int, y1: int, width: int, height: int) -> bool:
    """判断某像素周围是否已有超过两个同色邻居，用于识别抗锯齿边缘。"""
    x0, y0 = max(x1 - 1, 0), max(y1 - 1, 0)
    x2, y2 = min(x1 + 1, width - 1), min(y1 + 1, height - 1)
    pos = (y1 * width + x1) * 4
    zeroes = 1 if (x1 == x0 or x1 == x2 or y1 == y0 or y1 == y2) else 0
    for x in range(x0, x2 + 1):
        for y in range(y0, y2 + 1):
            if x == x1 and y == y1:
                continue
            pos2 = (y * width + x) * 4
            if (img[pos] == img[pos2] and img[pos + 1] == img[pos2 + 1]
                    and img[pos + 2] == img[pos2 + 2] and img[pos + 3] == img[pos2 + 3]):
                zeroes += 1
            if zeroes > 2:
                return True
    return False


def _antialiased(img: RGBA, x1: int, y1: int, width: int, height: int, img2: RGBA) -> bool:
    """pixelmatch 的抗锯齿判定：邻居亮度差落在两端且与另一张图同位置一致。"""
    x0, y0 = max(x1 - 1, 0), max(y1 - 1, 0)
    x2, y2 = min(x1 + 1, width - 1), min(y1 + 1, height - 1)
    pos = (y1 * width + x1) * 4
    zeroes = 1 if (x1 == x0 or x1 == x2 or y1 == y0 or y1 == y2) else 0
    lo = hi = 0.0
    lo_xy: tuple[int, int] | None = None
    hi_xy: tuple[int, int] | None = None
    for x in range(x0, x2 + 1):
        for y in range(y0, y2 + 1):
            if x == x1 and y == y1:
                continue
            delta = _color_delta(img, img, pos, (y * width + x) * 4, True)
            if delta == 0:
                zeroes += 1
                if zeroes > 2:
                    return False
            elif delta < lo:
                lo, lo_xy = delta, (x, y)
            elif delta > hi:
                hi, hi_xy = delta, (x, y)
    if lo == 0 or hi == 0 or lo_xy is None or hi_xy is None:
        return False
    return ((_has_many_siblings(img, lo_xy[0], lo_xy[1], width, height)
             and _has_many_siblings(img2, lo_xy[0], lo_xy[1], width, height))
            or (_has_many_siblings(img, hi_xy[0], hi_xy[1], width, height)
                and _has_many_siblings(img2, hi_xy[0], hi_xy[1], width, height)))


def compare(img1: RGBA, img2: RGBA, width: int, height: int, threshold: float = 0.1,
            include_aa: bool = False) -> CompareResult:
    """比对两张同尺寸 RGBA 图，返回差异像素数、占比和 diff 图字节。"""
    if width <= 0 or height <= 0:
        raise ValueError("宽高必须为正数")
    total = width * height
    if len(img1) < total * 4 or len(img2) < total * 4:
        raise ValueError("图像字节数与宽高不匹配")
    max_delta = 35215 * threshold * threshold
    diff = bytearray(total * 4)
    changed = 0
    for y in range(height):
        for x in range(width):
            pos = (y * width + x) * 4
            delta = _color_delta(img1, img2, pos, pos)
            if abs(delta) > max_delta:
                if not include_aa and (
                    _antialiased(img1, x, y, width, height, img2)
                    or _antialiased(img2, x, y, width, height, img1)
                ):
                    diff[pos:pos + 4] = _ANTIALIAS_COLOR
                    continue
                changed += 1
                diff[pos:pos + 4] = _DIFF_COLOR
            else:
                value = int(_blend(_rgb2y(img1[pos], img1[pos + 1], img1[pos + 2]),
                                   0.1 * img1[pos + 3] / 255))
                diff[pos:pos + 4] = bytes((value, value, value, 255))
    return {
        "diff_pixels": changed,
        "total_pixels": total,
        "ratio": changed / total,
        "diff_png": encode_png(width, height, diff),
    }


def downscale(width: int, height: int, rgba: RGBA, factor: int) -> tuple[int, int, bytearray]:
    """整数倍盒式降采样，用于把设备像素归一化到逻辑分辨率。"""
    if factor <= 1:
        return width, height, bytearray(rgba)
    out_width, out_height = width // factor, height // factor
    out = bytearray(out_width * out_height * 4)
    area = factor * factor
    for oy in range(out_height):
        for ox in range(out_width):
            red = green = blue = alpha = 0
            for dy in range(factor):
                base = ((oy * factor + dy) * width + ox * factor) * 4
                for dx in range(factor):
                    p = base + dx * 4
                    red += rgba[p]
                    green += rgba[p + 1]
                    blue += rgba[p + 2]
                    alpha += rgba[p + 3]
            o = (oy * out_width + ox) * 4
            out[o] = red // area
            out[o + 1] = green // area
            out[o + 2] = blue // area
            out[o + 3] = alpha // area
    return out_width, out_height, out
