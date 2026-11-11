#!/usr/bin/env python3
"""Build framework instrumentation against the integrating agent's compiled app classes."""
import argparse
import os
from pathlib import Path
import subprocess
import zipfile


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("--seed",action="store_true",help="Only build the schema1 seed against old version2")
    p.add_argument("--sdk",default="/workspace/toolchains/android-sdk")
    p.add_argument("--jdk",default="/workspace/toolchains/jdk-17.0.17+10")
    p.add_argument("--classes",default="app/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes")
    p.add_argument("--output",default="/workspace/toolchains/max11-instrumentation")
    a=p.parse_args();root=Path(__file__).resolve().parents[2]
    sdk,jdk=Path(a.sdk),Path(a.jdk);bt=sdk/"build-tools/35.0.0";android=sdk/"platforms/android-35/android.jar"
    app_classes=Path(a.classes).resolve();out=Path(a.output)/("seed" if a.seed else "verify");out.mkdir(parents=True,exist_ok=True)
    env=os.environ.copy();env["JAVA_HOME"]=str(jdk);env["PATH"]=str(jdk/"bin")+os.pathsep+env["PATH"]
    def run(*args):subprocess.run([str(x) for x in args],check=True,env=env)
    manifest=out/"AndroidManifest.xml"
    manifest.write_text('''<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="qa.max11.instrumentation">
      <uses-sdk android:minSdkVersion="26" android:targetSdkVersion="35" />
      <queries><package android:name="com.facebook.katana" /><package android:name="com.whatsapp" /><package android:name="qa.speech" /></queries>
      <application android:debuggable="true" android:label="MAX1.1 local QA"><uses-library android:name="android.test.runner" android:required="true" /></application>
      <instrumentation android:name="android.test.InstrumentationTestRunner" android:targetPackage="com.maxassistant.app" />
      </manifest>\n''')
    classes=out/"classes";classes.mkdir(exist_ok=True);dex=out/"dex";dex.mkdir(exist_ok=True)
    optional=list((sdk/"platforms/android-35/optional").glob("android.test.*.jar"))
    cp=os.pathsep.join(str(x) for x in [android,*optional,app_classes])
    src=root/"docs/qa/max11"
    sources=[src/"Max11UpgradeSeedTest.java"] if a.seed else [f for f in src.glob("*.java") if f.name!="Max11UpgradeSeedTest.java"]
    run(jdk/"bin/javac","-encoding","UTF-8","-source","17","-target","17","-cp",cp,"-d",classes,*sources)
    run(bt/"d8","--lib",android,"--classpath",app_classes,"--min-api","26","--output",dex,*classes.rglob("*.class"))
    unsigned=out/"unsigned.apk";run(bt/"aapt","package","-f","-M",manifest,"-I",android,"-F",unsigned)
    with zipfile.ZipFile(unsigned,"a",compression=zipfile.ZIP_DEFLATED) as apk:apk.write(dex/"classes.dex","classes.dex")
    aligned=out/"aligned.apk";run(bt/"zipalign","-f","4",unsigned,aligned)
    apk=out/"max11-test.apk";run(bt/"apksigner","sign","--ks",Path.home()/".android/debug.keystore","--ks-pass","pass:android","--key-pass","pass:android","--out",apk,aligned)
    run(bt/"apksigner","verify",apk);print(apk)


if __name__=="__main__":main()
