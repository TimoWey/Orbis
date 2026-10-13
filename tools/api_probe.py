"""Prints Minecraft API facts for engine work: members of classes, or the bytecode of single methods.

Reads .github/api-probe.txt, one request per line:
  <class> [regex]         members of the class whose javap line matches the regex
  -c <class> <method>     disassembly of every method with that name
Searches the Minecraft jars that Loom prepared in the Gradle cache.
"""
import glob
import os
import re
import subprocess
import sys

requests = ".github/api-probe.txt"
if not os.path.exists(requests):
    sys.exit(0)
jars = [j for j in glob.glob(os.path.expanduser("~/.gradle/caches/fabric-loom/**/minecraft*.jar"), recursive=True)
        if "sources" not in j]
if not jars:
    print("api-probe: no Minecraft jars found")
    sys.exit(0)
classpath = os.pathsep.join(jars)
for line in open(requests):
    parts = line.split()
    if not parts or parts[0].startswith("#"):
        continue
    if parts[0] == "-c":
        name, method = parts[1], parts[2]
        out = subprocess.run(["javap", "-p", "-c", "-cp", classpath, name], capture_output=True, text=True).stdout
        printing = False
        print(f"== {name}#{method} ==")
        for row in out.splitlines():
            if re.match(r"^  \S.*\(", row):
                printing = re.search(r"[ .]" + re.escape(method) + r"\(", row) is not None
            if printing:
                print(row)
    else:
        name = parts[0]
        pattern = re.compile(parts[1]) if len(parts) > 1 else None
        out = subprocess.run(["javap", "-p", "-cp", classpath, name], capture_output=True, text=True)
        print(f"== {name} ==")
        for row in (out.stdout + out.stderr).splitlines():
            if pattern is None or pattern.search(row):
                print(row)
