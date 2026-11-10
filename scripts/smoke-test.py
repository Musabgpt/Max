#!/usr/bin/env python3
"""ADB UI smoke checks; coordinates are derived exclusively from UI XML nodes.

Run with an already booted disposable emulator, never a personal phone:
  python3 scripts/smoke-test.py --serial emulator-5554 --apk app/build/outputs/apk/debug/app-debug.apk
Then optionally check a due task with the screen off (requires baseline synthetic task):
  python3 scripts/smoke-test.py --serial emulator-5554 --background-check
No messages, purchases, API calls or model downloads are performed.
"""
import argparse
import datetime
import json
import os
from pathlib import Path
import re
import sqlite3
import subprocess
import tempfile
import time
import xml.etree.ElementTree as ET


class Smoke:
    PACKAGE = "com.maxassistant.app"

    def __init__(self, args):
        self.args = args
        self.out = Path(args.output)
        self.out.mkdir(parents=True, exist_ok=True)
        (self.out / "screenshots").mkdir(exist_ok=True)
        self.results = []
        self.sequence = 0

    def adb(self, *args, binary=False, timeout=45):
        return subprocess.check_output([self.args.adb, "-s", self.args.serial, *args], timeout=timeout, text=not binary)

    def record(self, name, status, detail=""):
        item = {"check": name, "status": status, "detail": detail}
        self.results.append(item)
        print(json.dumps(item, ensure_ascii=False), flush=True)
        self.save()

    def save(self):
        payload = {"tested_at": datetime.datetime.now(datetime.timezone.utc).isoformat(), "serial": self.args.serial, "checks": self.results}
        (self.out / "smoke-results.json").write_text(json.dumps(payload, ensure_ascii=False, indent=2))

    def dump(self, name="ui"):
        self.sequence += 1
        raw = self.adb("exec-out", "uiautomator", "dump", "/dev/tty")
        start = raw.find("<?xml")
        end = raw.rfind("</hierarchy>")
        if start < 0 or end < 0:
            raise RuntimeError("UI hierarchy unavailable: " + raw[:300])
        xml = raw[start:end + len("</hierarchy>")]
        (self.out / f"{self.sequence:02d}-{name}.xml").write_text(xml)
        return list(ET.fromstring(xml).iter("node"))

    def find(self, label, nodes=None):
        nodes = self.dump() if nodes is None else nodes
        matches = [n for n in nodes if label in n.get("text", "") or label in n.get("content-desc", "")]
        matches.sort(key=lambda n: (n.get("clickable") != "true", n.get("text") != label))
        return matches[0] if matches else None

    def tap_node(self, node):
        numbers = [int(n) for n in re.findall(r"\d+", node.get("bounds", ""))]
        if len(numbers) != 4:
            raise RuntimeError("No tap bounds on selected node")
        x1, y1, x2, y2 = numbers
        self.adb("shell", "input", "tap", str((x1+x2)//2), str((y1+y2)//2))
        time.sleep(0.5)

    def tap(self, label, scroll=False):
        for attempt in range(5 if scroll else 1):
            nodes = self.dump()
            node = self.find(label, nodes)
            if node is not None:
                self.tap_node(node)
                return
            if scroll:
                scrolls = [n for n in nodes if n.get("scrollable") == "true"]
                if scrolls:
                    coords = [int(x) for x in re.findall(r"\d+", scrolls[0].get("bounds"))]
                    x1, y1, x2, y2 = coords
                    x = (x1+x2)//2
                    self.adb("shell", "input", "swipe", str(x), str(y2-100), str(x), str(y1+120), "500")
                    time.sleep(0.3)
        raise AssertionError("UI element not found: " + label)

    def assert_text(self, label):
        if self.find(label) is None:
            raise AssertionError("Expected text absent: " + label)

    def fill_fields(self, values):
        for index, value in enumerate(values):
            # Opening an IME can move a dialog. Never reuse old field coordinates.
            edits = [n for n in self.dump() if n.get("class") == "android.widget.EditText"]
            if len(edits) < len(values):
                raise AssertionError(f"Expected {len(values)} editable fields, found {len(edits)}")
            self.tap_node(edits[index])
            time.sleep(0.5)
            self.adb("shell", "input", "text", value.replace(" ", "%s"))
        ime = self.adb("shell", "dumpsys", "input_method")
        if "mInputShown=true" in ime:
            self.adb("shell", "input", "keyevent", "4")
        time.sleep(0.3)

    def screenshot(self, name):
        path = self.out / "screenshots" / (name + ".png")
        path.write_bytes(self.adb("exec-out", "screencap", "-p", binary=True))

    def launch(self):
        self.adb("shell", "am", "start", "-W", "-n", self.PACKAGE + "/.MainActivity")
        time.sleep(1)

    def background_failure_check(self):
        """Exercise a due synthetic task with no model, including screen-off work.

        Only use on a disposable emulator: modifies its debug APK database fixture.
        It does not alter the production app or require an API key.
        """
        self.adb("shell", "am", "force-stop", self.PACKAGE)
        with tempfile.TemporaryDirectory(prefix="max-qa-") as directory:
            base = Path(directory) / "max-local.db"
            for suffix in ("", "-wal", "-shm"):
                try:
                    data = self.adb("exec-out", "run-as", self.PACKAGE, "cat", "databases/max-local.db" + suffix, binary=True)
                    Path(str(base) + suffix).write_bytes(data)
                except subprocess.CalledProcessError:
                    if not suffix:
                        raise
            db = sqlite3.connect(base)
            db.execute("PRAGMA wal_checkpoint(TRUNCATE)")
            task = db.execute("SELECT id,title FROM tasks ORDER BY id DESC LIMIT 1").fetchone()
            if task is None:
                raise AssertionError("Create a synthetic task before background check")
            device_now = int(self.adb("shell", "date", "+%s").strip()) * 1000
            db.execute("UPDATE tasks SET scheduled_at=?, status='pending', result='' WHERE id=?", (device_now + 75000, task[0]))
            db.commit()
            db.execute("PRAGMA wal_checkpoint(TRUNCATE)")
            db.close()
            self.adb("push", str(base), "/data/local/tmp/max-qa-fixture.db")
            self.adb("shell", "run-as", self.PACKAGE, "cp", "/data/local/tmp/max-qa-fixture.db", "databases/max-local.db")
            self.adb("shell", "run-as", self.PACKAGE, "rm", "-f", "databases/max-local.db-wal", "databases/max-local.db-shm")
        self.launch()
        self.tap("الإعدادات")
        self.tap("تشغيل العمل بالخلفية", scroll=True)
        self.adb("shell", "input", "keyevent", "223")  # sleep key; no unlocking bypass
        power = self.adb("shell", "dumpsys", "power")
        (self.out / "screen-off-power.txt").write_text(power)
        if "mWakefulness=Asleep" not in power and "mWakefulness=Dozing" not in power:
            raise AssertionError("Emulator did not turn the screen off")
        deadline = time.monotonic() + 130
        found = None
        while time.monotonic() < deadline:
            # Read a transactionally consistent debug SQLite fixture off-device.
            with tempfile.TemporaryDirectory(prefix="max-qa-result-") as directory:
                base = Path(directory) / "max-local.db"
                for suffix in ("", "-wal", "-shm"):
                    try:
                        Path(str(base)+suffix).write_bytes(self.adb("exec-out", "run-as", self.PACKAGE, "cat", "databases/max-local.db"+suffix, binary=True))
                    except subprocess.CalledProcessError:
                        if not suffix:
                            raise
                db=sqlite3.connect(base)
                found=db.execute("SELECT status,result FROM tasks WHERE id=?", (task[0],)).fetchone()
                db.close()
            if found and found[0] in ("failed", "completed"):
                break
            time.sleep(2)
        self.adb("shell", "input", "keyevent", "224")
        self.adb("shell", "input", "keyevent", "82")  # default unsecured emulator keyguard
        self.launch()
        self.tap("مهامي")
        self.screenshot("background-result")
        if not found or found[0] != "failed" or "النموذج" not in found[1]:
            raise AssertionError("Expected missing-model background failure, got " + repr(found))
        self.record("background_task_while_screen_off", "passed", "Synthetic scheduled_at fixture advanced; recorded failure for missing model without crash. " + found[1])

    def run(self):
        if self.args.apk:
            self.adb("install", "-r", self.args.apk, timeout=180)
        # Some old system images reject clearing the main log while logd starts.
        # Crash evidence still remains readable, so this must not prevent UI QA.
        try:
            self.adb("logcat", "-b", "crash", "-c")
        except subprocess.CalledProcessError:
            self.record("clear_crash_log", "unavailable", "Continue using existing crash buffer")
        self.launch()
        nodes = self.dump("launch")
        if self.find("استكشاف التطبيق", nodes) is not None:
            self.tap("استكشاف التطبيق")
        self.tap("مساعدي")
        self.assert_text("مساعدي")
        self.screenshot("home")
        self.record("install_and_arabic_home", "passed")

        for label in ("مهامي", "الوارد", "ذاكرتي", "الإعدادات", "مساعدي"):
            self.tap(label)
            self.assert_text(label)
        self.record("all_five_tabs", "passed")

        self.tap("ذاكرتي")
        self.tap("إضافة ذكرى")
        memory_name = "QA-memory-" + str(int(time.time()))
        self.fill_fields([memory_name, "I prefer concise answers"])
        self.tap("حفظ")
        self.assert_text(memory_name)
        self.adb("shell", "am", "force-stop", self.PACKAGE)
        self.launch()
        self.tap("ذاكرتي")
        self.assert_text(memory_name)
        self.screenshot("memory")
        self.record("memory_persists_across_process_restart", "passed", memory_name)

        self.tap("مهامي")
        self.tap("مهمة جديدة")
        task_name = "QA-task-" + str(int(time.time()))
        self.fill_fields([task_name, "Remind me to review a draft"])
        self.tap("حفظ")
        self.assert_text(task_name)
        self.assert_text("مجدولة")
        self.screenshot("tasks")
        self.record("create_scheduled_task", "passed", task_name)

        self.tap("وضع العمل")
        self.assert_text("وضع النوم")
        self.tap("وضع النوم")
        self.assert_text("وضع العمل")
        self.record("work_sleep_switch", "passed")

        self.tap("مساعدي")
        self.fill_fields(["Hello Max"])
        self.tap("إرسال")
        self.assert_text("النموذج غير محمّل بعد")
        self.screenshot("model-needed")
        self.adb("shell", "input", "keyevent", "4")
        self.record("missing_local_model_is_actionable", "passed")

        self.adb("shell", "settings", "put", "system", "accelerometer_rotation", "0")
        self.adb("shell", "settings", "put", "system", "user_rotation", "1")
        time.sleep(1)
        self.adb("shell", "settings", "put", "system", "user_rotation", "0")
        time.sleep(1)
        self.assert_text("مساعدي")
        self.record("rotation_lifecycle", "passed")

        crash = self.adb("logcat", "-b", "crash", "-d")
        (self.out / "crash-logcat.txt").write_text(crash)
        if "FATAL EXCEPTION" in crash or "Fatal signal" in crash:
            raise AssertionError("Crash recorded; inspect docs/qa/crash-logcat.txt")
        self.record("crash_buffer", "passed", "No fatal exception or native fatal signal")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", default=os.environ.get("ANDROID_HOME", "/workspace/toolchains/android-sdk") + "/platform-tools/adb")
    parser.add_argument("--serial", default="emulator-5554")
    parser.add_argument("--apk")
    parser.add_argument("--output", default="docs/qa")
    parser.add_argument("--background-check", action="store_true", help="Advance a synthetic debug task fixture and check screen-off failure handling")
    args = parser.parse_args()
    smoke = Smoke(args)
    try:
        if args.background_check:
            previous = smoke.out / "smoke-results.json"
            if previous.exists():
                smoke.results = json.loads(previous.read_text())["checks"]
            smoke.sequence = 100
            smoke.background_failure_check()
        else:
            smoke.run()
    except Exception as error:
        smoke.record("smoke_execution", "failed", str(error))
        raise


if __name__ == "__main__":
    main()
