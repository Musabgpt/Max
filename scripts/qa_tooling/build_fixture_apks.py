#!/usr/bin/env python3
"""Build local-only MAX QA fixtures with the installed official SDK, without Gradle."""
import argparse
import os
from pathlib import Path
import shutil
import subprocess
import zipfile


def run(*args):
    subprocess.run([str(a) for a in args], check=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--sdk", default="/workspace/toolchains/android-sdk")
    parser.add_argument("--jdk", default="/workspace/toolchains/jdk-17.0.17+10")
    parser.add_argument("--output", default="/workspace/toolchains/max11-fixtures")
    parser.add_argument("--keystore", default=str(Path.home()/".android/debug.keystore"))
    parser.add_argument("--only",choices=("facebook","whatsapp","speech","home"))
    parser.add_argument("--legacy-speech-binding",action="store_true",help="API26 binds RecognitionService directly, before the modern system broker")
    args = parser.parse_args()
    sdk,jdk,out = Path(args.sdk),Path(args.jdk),Path(args.output)
    root = Path(__file__).resolve().parents[2]
    bt = sdk/"build-tools/35.0.0"
    android = sdk/"platforms/android-35/android.jar"
    env = os.environ.copy();env["JAVA_HOME"]=str(jdk)
    env["PATH"]=str(jdk/"bin")+os.pathsep+env["PATH"]
    for kind,package in (("facebook","com.facebook.katana"),("whatsapp","com.whatsapp"),("speech","qa.speech"),("home","qa.home")):
        if args.only and args.only!=kind:continue
        d=out/kind;d.mkdir(parents=True,exist_ok=True)
        source=root/"docs/qa/max11/fixtures"/(kind if kind in ("speech","home") else "social")
        res=d/"res";res.mkdir(exist_ok=True)
        if kind=="home":
            components='''<activity android:name="qa.home.FixtureHomeActivity" android:exported="true"><intent-filter>
              <action android:name="android.intent.action.MAIN" /><category android:name="android.intent.category.HOME" />
              <category android:name="android.intent.category.DEFAULT" /><category android:name="android.intent.category.LAUNCHER" />
              </intent-filter></activity>'''
        elif kind=="speech":
            (res/"xml").mkdir(exist_ok=True)
            (res/"xml/recognition_service.xml").write_text('<recognition-service xmlns:android="http://schemas.android.com/apk/res/android" />\n')
            binding_permission='' if args.legacy_speech_binding else ' android:permission="android.permission.BIND_SPEECH_RECOGNITION_SERVICE"'
            components='''<service android:name="qa.speech.FixtureRecognitionService" android:exported="true"'''+binding_permission+'''>
              <intent-filter><action android:name="android.speech.RecognitionService" /></intent-filter>
              <meta-data android:name="android.speech" android:resource="@xml/recognition_service" /></service>
              <activity android:name="qa.speech.FixtureSpeechActivity" android:exported="true"><intent-filter>
                <action android:name="android.speech.action.RECOGNIZE_SPEECH" /><category android:name="android.intent.category.DEFAULT" /></intent-filter></activity>
              <provider android:name="qa.speech.FixtureSpeechProvider" android:authorities="qa.speech.state" android:exported="true" />'''
        else:
            (res/"values").mkdir(exist_ok=True);shutil.copyfile(source/"ids.xml",res/"values/ids.xml")
            share='''<intent-filter><action android:name="android.intent.action.SEND" /><category android:name="android.intent.category.DEFAULT" /><data android:mimeType="text/plain" /></intent-filter>''' if kind=="whatsapp" else ""
            components=f'''<activity android:name="qa.fixture.FixtureActivity" android:exported="true" android:launchMode="singleTask">
              <intent-filter><action android:name="android.intent.action.MAIN" /><category android:name="android.intent.category.LAUNCHER" /></intent-filter>{share}</activity>
              <provider android:name="qa.fixture.FixtureProvider" android:authorities="qa.{kind}.state" android:exported="true" />'''
        manifest=d/"AndroidManifest.xml"
        manifest.write_text(f'''<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="{package}">
          <uses-sdk android:minSdkVersion="26" android:targetSdkVersion="35" />
          <application android:label="{kind.title()} QA Fixture" android:debuggable="true" android:theme="@android:style/Theme.Material.Light.NoActionBar" android:allowBackup="false">{components}</application>
          </manifest>\n''')
        classes=d/"classes";classes.mkdir(exist_ok=True)
        subprocess.run([str(jdk/"bin/javac"),"-encoding","UTF-8","-source","17","-target","17","-cp",str(android),"-d",str(classes),*[str(p) for p in source.glob("*.java")]],check=True,env=env)
        dex=d/"dex";dex.mkdir(exist_ok=True)
        subprocess.run([str(bt/"d8"),"--lib",str(android),"--min-api","26","--output",str(dex),*[str(p) for p in classes.rglob("*.class")]],check=True,env=env)
        unsigned=d/"unsigned.apk"
        run(bt/"aapt","package","-f","-M",manifest,"-I",android,"-S",res,"-F",unsigned)
        with zipfile.ZipFile(unsigned,"a",compression=zipfile.ZIP_DEFLATED) as apk:
            apk.write(dex/"classes.dex","classes.dex")
        aligned=d/"aligned.apk";run(bt/"zipalign","-f","4",unsigned,aligned)
        apk=d/(kind+"-fixture.apk")
        subprocess.run([str(bt/"apksigner"),"sign","--ks",args.keystore,"--ks-pass","pass:android","--key-pass","pass:android","--out",str(apk),str(aligned)],check=True,env=env)
        subprocess.run([str(bt/"apksigner"),"verify",str(apk)],check=True,env=env)
        print(apk,flush=True)


if __name__ == "__main__":
    main()
