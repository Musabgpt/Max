#!/usr/bin/env python3
"""Run real Android QA with a localhost request observer; never installs or signs production builds."""
import argparse
import datetime
from http.server import BaseHTTPRequestHandler,ThreadingHTTPServer
import json
import re
from pathlib import Path
import subprocess
import threading


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("--serial",required=True)
    p.add_argument("--adb",default="/workspace/toolchains/android-sdk/platform-tools/adb")
    p.add_argument("--test-class",default="qa.max11.Max11UpgradeVerifyTest,qa.max11.Max11HtmlTest,qa.max11.Max11DeviceTest,qa.max11.Max11VoiceTest")
    p.add_argument("--output",default="docs/qa/max11/run")
    p.add_argument("--expected-tests",type=int,default=15)
    a=p.parse_args();out=Path(a.output);out.mkdir(parents=True,exist_ok=True)
    requests=[];lock=threading.Lock()
    class Handler(BaseHTTPRequestHandler):
        def do_GET(self):
            with lock:requests.append({"path":self.path,"at":datetime.datetime.now(datetime.timezone.utc).isoformat()})
            self.send_response(200);self.send_header("Content-Type","text/plain");self.end_headers();self.wfile.write(b"MAX local QA observer\n")
        def log_message(self,*args):pass
    server=ThreadingHTTPServer(("127.0.0.1",8181),Handler);thread=threading.Thread(target=server.serve_forever,daemon=True);thread.start()
    def adb(*args,**kwargs):return subprocess.run([a.adb,"-s",a.serial,*args],check=True,**kwargs)
    previous=adb("shell","settings","get","secure","enabled_accessibility_services",capture_output=True,text=True).stdout.strip()
    previous_enabled=adb("shell","settings","get","secure","accessibility_enabled",capture_output=True,text=True).stdout.strip()
    started=datetime.datetime.now(datetime.timezone.utc).isoformat()
    try:
        adb("reverse","tcp:8181","tcp:8181")
        with (out/"instrumentation.txt").open("w") as log:
            adb("shell","am","instrument","-w","-r","-e","class",a.test_class,"qa.max11.instrumentation/android.test.InstrumentationTestRunner",stdout=log,stderr=subprocess.STDOUT)
        log=(out/"instrumentation.txt").read_text()
        blocked=[r for r in requests if r["path"]!="/native-control"]
        match=re.search(r"OK \((\d+) tests?\)",log);observed=int(match.group(1)) if match else 0
        native=any(r["path"]=="/native-control" for r in requests)
        sandbox=any(name=="qa.max11.Max11HtmlTest" or "#testSandboxBlocks" in name for name in a.test_class.split(","))
        passed=observed==a.expected_tests and observed>0 and "FAILURES" not in log and "INSTRUMENTATION_FAILED" not in log and not blocked and (native or not sandbox)
        (out/"network-observer.json").write_text(json.dumps({"requests":requests,"unexpectedRequests":blocked,"nativeControlObserved":native},indent=2)+"\n")
        (out/"result.json").write_text(json.dumps({"started":started,"serial":a.serial,"classes":a.test_class,"expectedTests":a.expected_tests,"observedTests":observed,"passed":passed,"scope":"Official emulator; fixture social/speech providers; no real account or microphone hardware test"},indent=2)+"\n")
        print(log)
        if not passed:raise SystemExit("Instrumentation or blocked-network assertion failed; inspect saved logs")
    finally:
        # Preserve observer evidence even if instrumentation is interrupted. Cleanup
        # errors must not hide the original failure or prevent the server closing.
        with lock:captured=list(requests)
        (out/"network-observer.json").write_text(json.dumps({"requests":captured,"unexpectedRequests":[r for r in captured if r["path"]!="/native-control"],"nativeControlObserved":any(r["path"]=="/native-control" for r in captured)},indent=2)+"\n")
        cleanup=[]
        commands=[("shell","settings","delete","secure","enabled_accessibility_services") if previous in ("","null") else ("shell","settings","put","secure","enabled_accessibility_services",previous),
                  ("shell","settings","delete","secure","accessibility_enabled") if previous_enabled in ("","null") else ("shell","settings","put","secure","accessibility_enabled",previous_enabled),
                  ("reverse","--remove","tcp:8181")]
        try:
            for command in commands:
                try:adb(*command)
                except subprocess.CalledProcessError as error:cleanup.append({"command":command[0],"returncode":error.returncode})
        finally:server.shutdown();server.server_close()
        if cleanup:(out/"cleanup-errors.json").write_text(json.dumps(cleanup,indent=2)+"\n")


if __name__=="__main__":main()
