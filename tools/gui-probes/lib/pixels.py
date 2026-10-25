"""纯标准库的 PNG 编解码与绘图基元。

用途：把客户端落盘的截图拼成 contact sheet，并提供等比缩放、画布填充与叠加所需的原语。
不依赖 Pillow 或外部服务。

边界：
- decode_png 只支持 8 bit、非隔行的灰度 / 灰度+alpha / 真彩 / 真彩+alpha PNG；
  索引色（color type 3）、16 bit、隔行 PNG 明确报错。
- 本模块只负责「解码、缩放、拼图、编码」，不判断两张图是否相同或差异多少；
  截图是否合格由人和 Agent 直接读图决定。
"""

import struct
import zlib
from collections.abc import Iterator

RGBA = bytes | bytearray

_CHANNELS = {0: 1, 2: 3, 4: 2, 6: 4}

# 绘图缓存：UI 截图的整行内容高度重复，按行宽缓存一次即可。
_MASK_CACHE: dict[int, tuple[int, int]] = {}
_ALPHA_ROWS: dict[int, bytes] = {}


def _chunks(blob: bytes) -> Iterator[tuple[bytes, bytes]]:
    """按 PNG 规范逐块产出 (类型, 数据)。"""
    pos = 8
    while pos + 8 <= len(blob):
        (length,) = struct.unpack(">I", blob[pos:pos + 4])
        tag = blob[pos + 4:pos + 8]
        body = blob[pos + 8:pos + 8 + length]
        pos += 12 + length
        yield tag, body


def png_size(blob: bytes) -> tuple[int, int]:
    """只读 IHDR 取宽高，不解压、不解码像素；用于缩略图的尺寸快查。"""
    for tag, body in _chunks(blob):
        if tag == b"IHDR":
            return struct.unpack(">II", body[:8])
        if tag == b"IEND":
            break
    raise ValueError("PNG 缺少 IHDR")


def _masks(length: int) -> tuple[int, int]:
    """16 位以上整数的逐字节低位掩码与高位掩码，按行宽缓存。"""
    masks = _MASK_CACHE.get(length)
    if masks is None:
        masks = (int.from_bytes(b"\x7f" * length, "little"),
                 int.from_bytes(b"\x80" * length, "little"))
        _MASK_CACHE[length] = masks
    return masks


def _add_up(current: bytes, above: bytes) -> bytes:
    """PNG Up 过滤器：逐字节加 modulo 256，一整个扫描行只用一次整数运算。"""
    length = len(current)
    low_mask, high_mask = _masks(length)
    left = int.from_bytes(current, "little")
    right = int.from_bytes(above, "little")
    low = (left & low_mask) + (right & low_mask)
    merged = (low & low_mask) | ((low & high_mask) ^ ((left ^ right) & high_mask))
    return merged.to_bytes(length, "little")


def _unfilter(line: bytearray, filter_type: int, prev: bytes, channels: int) -> None:
    """还原 Sub / Average / Paeth 三种需要左邻的过滤器；未知类型按 0 处理。"""
    stride = len(line)
    if filter_type == 1:
        for i in range(channels, stride):
            line[i] = (line[i] + line[i - channels]) & 255
    elif filter_type == 3:
        for i in range(channels):
            line[i] = (line[i] + (prev[i] >> 1)) & 255
        for i in range(channels, stride):
            line[i] = (line[i] + ((line[i - channels] + prev[i]) >> 1)) & 255
    elif filter_type == 4:
        for i in range(channels):
            line[i] = (line[i] + prev[i]) & 255
        for i in range(channels, stride):
            left = line[i - channels]
            up = prev[i]
            upper_left = prev[i - channels]
            p = left + up - upper_left
            pa = p - left
            if pa < 0:
                pa = -pa
            pb = p - up
            if pb < 0:
                pb = -pb
            pc = p - upper_left
            if pc < 0:
                pc = -pc
            if pa <= pb and pa <= pc:
                predictor = left
            elif pb <= pc:
                predictor = up
            else:
                predictor = upper_left
            line[i] = (line[i] + predictor) & 255


def _alpha_row(width: int) -> bytes:
    row = _ALPHA_ROWS.get(width)
    if row is None:
        row = b"\xff" * width
        _ALPHA_ROWS[width] = row
    return row


def decode_png(blob: bytes) -> tuple[int, int, bytearray]:
    """把 PNG 字节解码为 (宽, 高, RGBA 字节序列)，alpha 缺失时补 255。

    快速路径：整行 filter 0 直接切片搬运，Up 过滤器用整数 SWAR 一次算完整行，
    4 通道图不再逐像素拷贝；灰度 / 真彩图用步长切片一次性展开成 RGBA。
    """
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
    prev = b"\x00" * stride
    pos = 0
    for y in range(height):
        filter_type = raw[pos]
        pos += 1
        body = raw[pos:pos + stride]
        if filter_type == 0:
            line = body
        elif filter_type == 2:
            line = _add_up(body, prev)
        else:
            line = bytearray(body)
            _unfilter(line, filter_type, prev, channels)
        pos += stride
        base = y * width * 4
        if channels == 4:
            out[base:base + stride] = line
        else:
            row = bytearray(width * 4)
            if channels == 3:
                row[0::4] = line[0::3]
                row[1::4] = line[1::3]
                row[2::4] = line[2::3]
            elif channels == 1:
                row[0::4] = line
                row[1::4] = line
                row[2::4] = line
            else:
                gray = line[0::2]
                row[0::4] = gray
                row[1::4] = gray
                row[2::4] = gray
                row[3::4] = line[1::2]
            if channels != 2:
                row[3::4] = _alpha_row(width)
            out[base:base + width * 4] = row
        prev = line
    return width, height, out


def encode_png(width: int, height: int, rgba: RGBA) -> bytes:
    """把 RGBA 字节序列编码成 8 bit 真彩+alpha 的 PNG 字节。"""
    if width <= 0 or height <= 0:
        raise ValueError("宽高必须为正数")
    stride = width * 4
    if len(rgba) != stride * height:
        raise ValueError("RGBA 字节数与宽高不匹配")

    raw = b"".join(b"\x00" + bytes(rgba[y * stride:(y + 1) * stride]) for y in range(height))

    def chunk(tag: bytes, data: bytes) -> bytes:
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    ihdr = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr)
            + chunk(b"IDAT", zlib.compress(raw, 6)) + chunk(b"IEND", b""))


def scale_to_fit(width: int, height: int, rgba: RGBA,
                 box_width: int, box_height: int) -> tuple[int, int, bytearray]:
    """等比缩放到 box 内（最近邻，不放大），用于 contact sheet 的格子图。"""
    if width <= 0 or height <= 0 or box_width <= 0 or box_height <= 0:
        raise ValueError("宽高必须为正数")
    scale = min(box_width / width, box_height / height, 1.0)
    out_width = max(1, min(box_width, int(round(width * scale))))
    out_height = max(1, min(box_height, int(round(height * scale))))
    if out_width == width and out_height == height:
        return out_width, out_height, bytearray(rgba)
    stride = width * 4
    xmap = [min(width - 1, ox * width // out_width) * 4 for ox in range(out_width)]
    out = bytearray(out_width * out_height * 4)
    row_stride = out_width * 4
    for oy in range(out_height):
        base = min(height - 1, oy * height // out_height) * stride
        row = b"".join(rgba[base + dx:base + dx + 4] for dx in xmap)
        out[oy * row_stride:(oy + 1) * row_stride] = row
    return out_width, out_height, out


def new_canvas(width: int, height: int, color: tuple[int, int, int, int] = (27, 31, 39, 255)) -> bytearray:
    """创建纯色 RGBA 画布。"""
    if width <= 0 or height <= 0:
        raise ValueError("宽高必须为正数")
    return bytearray(bytes(color) * (width * height))


def fill_rect(canvas: bytearray, width: int, height: int, x: int, y: int,
              rect_width: int, rect_height: int, color: tuple[int, int, int, int]) -> None:
    """在画布上填充矩形，超出画布的部分自动裁掉。"""
    x0, y0 = max(0, x), max(0, y)
    x1, y1 = min(width, x + rect_width), min(height, y + rect_height)
    if x0 >= x1 or y0 >= y1:
        return
    row = bytes(color) * (x1 - x0)
    for yy in range(y0, y1):
        offset = (yy * width + x0) * 4
        canvas[offset:offset + len(row)] = row


def blit(dst: bytearray, dst_width: int, dst_height: int,
         src: RGBA, src_width: int, src_height: int, x: int, y: int) -> None:
    """把 src 覆盖到 dst 的 (x, y)，不做 alpha 混合（截图本身不透明）。"""
    src_x = max(0, -x)
    src_y = max(0, -y)
    dst_x = max(0, x)
    dst_y = max(0, y)
    copy_width = min(src_width - src_x, dst_width - dst_x)
    copy_height = min(src_height - src_y, dst_height - dst_y)
    if copy_width <= 0 or copy_height <= 0:
        return
    length = copy_width * 4
    for row in range(copy_height):
        src_offset = ((src_y + row) * src_width + src_x) * 4
        dst_offset = ((dst_y + row) * dst_width + dst_x) * 4
        dst[dst_offset:dst_offset + length] = src[src_offset:src_offset + length]
