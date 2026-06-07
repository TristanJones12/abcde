from pathlib import Path
import shutil

# Relative to workspace root, not home directory
APK_ARTIFACTS_DIR = Path("apk-artifacts")
REPO_APK_DIR = Path("repo/apk")

shutil.rmtree(REPO_APK_DIR, ignore_errors=True)
REPO_APK_DIR.mkdir(parents=True, exist_ok=True)

for apk in APK_ARTIFACTS_DIR.glob("**/*.apk"):
    apk_name = apk.name.replace("-release.apk", ".apk")
    shutil.move(str(apk), REPO_APK_DIR / apk_name)

print(f"Moved APKs: {list(REPO_APK_DIR.iterdir())}")
