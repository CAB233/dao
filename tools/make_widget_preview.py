#!/usr/bin/env python3
"""从设备截图生成桌面小组件的预览图（drawable-*/widget_preview.png）。

为什么用截图而不是照着布局重画一遍：预览图只要跟真实渲染有半点出入，用户在选择器里看到的
就和添加出来的不一样。截图是唯一"所见即所得"的来源。

用法：
    python tools/make_widget_preview.py shot.png 67,223,1013,1441
    python tools/make_widget_preview.py shot.png "[67,223][1013,1441]" --radius-px 55

参数：
    screenshot   设备截图（adb shell screencap -p /sdcard/w.png && adb pull）
    rect         裁切矩形，widget_root 的 bounds，两种写法都认：
                 uiautomator dump 里的 "[x1,y1][x2,y2]" 或 "x1,y1,x2,y2"
    --radius-px  圆角半径（像素）。widget_background 是 20dp，按截图密度的 20*density 传
                 （1080p/440dpi 这类设备约 55）。不传就是直角。

输出：res/drawable-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/widget_preview.png，
以 mdpi 宽度 264px 为基准等比缩放，圆角外做成透明。
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

try:
    from PIL import Image, ImageDraw
except ImportError:  # pragma: no cover - 只为了给出人话提示
    sys.exit("需要 Pillow：pip install pillow")

# mdpi 基准宽度；与改版前的预览图一致，选择器里的占位大小才不会跳
BASE_WIDTH = 264
DENSITIES = {"mdpi": 1.0, "hdpi": 1.5, "xhdpi": 2.0, "xxhdpi": 3.0, "xxxhdpi": 4.0}


def parse_rect(text: str) -> tuple[int, int, int, int]:
    """认 "[x1,y1][x2,y2]" 与 "x1,y1,x2,y2" 两种写法。"""
    numbers = [int(n) for n in re.findall(r"-?\d+", text)]
    if len(numbers) != 4:
        raise argparse.ArgumentTypeError(f"裁切矩形要有 4 个数字，收到：{text!r}")
    x1, y1, x2, y2 = numbers
    if x2 <= x1 or y2 <= y1:
        raise argparse.ArgumentTypeError(f"裁切矩形不合法：{text!r}")
    return x1, y1, x2, y2


def rounded_mask(size: tuple[int, int], radius: float) -> Image.Image:
    mask = Image.new("L", size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, size[0] - 1, size[1] - 1), radius=radius, fill=255)
    return mask


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("screenshot", type=Path)
    parser.add_argument("rect", type=parse_rect)
    parser.add_argument("--radius-px", type=float, default=0.0)
    parser.add_argument(
        "--res-dir",
        type=Path,
        default=Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "res",
    )
    args = parser.parse_args()

    if not args.screenshot.is_file():
        sys.exit(f"找不到截图：{args.screenshot}")

    shot = Image.open(args.screenshot).convert("RGBA")
    card = shot.crop(args.rect)
    scale = BASE_WIDTH / card.width
    radius = args.radius_px * scale

    for density, factor in DENSITIES.items():
        width = round(BASE_WIDTH * factor)
        height = round(card.height * scale * factor)
        resized = card.resize((width, height), Image.LANCZOS)
        # 圆角在放大之后按同比例重画，避免先切圆角再缩放导致的锯齿
        resized.putalpha(rounded_mask((width, height), radius * factor))
        out_dir = args.res_dir / f"drawable-{density}"
        out_dir.mkdir(parents=True, exist_ok=True)
        out_path = out_dir / "widget_preview.png"
        resized.save(out_path)
        print(f"{out_path}  {width}x{height}")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
