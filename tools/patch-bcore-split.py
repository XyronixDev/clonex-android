#!/usr/bin/env python3
"""Apply the split-APK patch to a NewBlackbox checkout, for CloneX.

Usage: python3 tools/patch-bcore-split.py <path-to-newblackbox-checkout>

- Copies the 4 patched Java files from tools/bcore-split-patch/ over the
  upstream files (they are MaxCloner's split support, Apache-2.0).
- Adds "/data/data/com.clonex.space" to the hidden-paths list in
  Bcore/src/main/cpp/Utils/AntiDetection.cpp.
- Verifies: the 4 files are byte-identical to the patch dir, and the
  AntiDetection line is present. Exits non-zero on any failure.
"""
import hashlib
import pathlib
import sys

PATCH_FILES = {
    "Bcore/src/main/java/top/niunaijun/blackbox/core/env/BEnvironment.java": "BEnvironment.java",
    "Bcore/src/main/java/top/niunaijun/blackbox/core/system/pm/installer/CopyExecutor.java": "CopyExecutor.java",
    "Bcore/src/main/java/top/niunaijun/blackbox/core/system/pm/PackageManagerCompat.java": "PackageManagerCompat.java",
    "Bcore/src/main/java/top/niunaijun/blackbox/app/BActivityThread.java": "BActivityThread.java",
}
ANTI_DETECTION = "Bcore/src/main/cpp/Utils/AntiDetection.cpp"
ANCHOR = '    "/data/data/top.niunaijun.blackboxa",\n'
ADD_LINE = '    "/data/data/com.clonex.space",\n'


def sha(p: pathlib.Path) -> str:
    return hashlib.sha256(p.read_bytes()).hexdigest()


def main() -> int:
    if len(sys.argv) != 2:
        print("usage: patch-bcore-split.py <newblackbox-checkout>", file=sys.stderr)
        return 2
    nb = pathlib.Path(sys.argv[1])
    here = pathlib.Path(__file__).resolve().parent
    patch_dir = here / "bcore-split-patch"
    for rel, name in PATCH_FILES.items():
        src = patch_dir / name
        dst = nb / rel
        assert src.is_file(), f"missing patch file {src}"
        assert dst.is_file(), f"missing upstream file {dst}"
        dst.write_bytes(src.read_bytes())
        assert sha(dst) == sha(src), f"verify failed for {rel}"
        print(f"patched {rel}")
    ad = nb / ANTI_DETECTION
    assert ad.is_file(), f"missing {ANTI_DETECTION}"
    text = ad.read_text(encoding="utf-8")
    if ADD_LINE.strip() not in text:
        assert ANCHOR in text, "AntiDetection anchor not found"
        text = text.replace(ANCHOR, ANCHOR + ADD_LINE, 1)
        ad.write_text(text, encoding="utf-8")
    assert ADD_LINE.strip() in ad.read_text(encoding="utf-8")
    print(f"patched {ANTI_DETECTION} (added com.clonex.space)")
    print("SPLIT PATCH APPLIED + VERIFIED")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
