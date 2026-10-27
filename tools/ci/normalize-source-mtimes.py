#!/usr/bin/env python3
"""
Copyright (c) 2026 The Brave Authors. All rights reserved.
This Source Code Form is subject to the terms of the Mozilla Public
License, v. 2.0. If a copy of the MPL was not distributed with this file,
You can obtain one at https://mozilla.org/MPL/2.0/.

Restores file modification timestamps (mtime) based on their latest Git commit timestamp.
Prevents false invalidation of object files and rebuild loops in Ninja when restoring build checkpoints.
"""

import os
import subprocess
import sys

def main():
    repo_root = sys.argv[1] if len(sys.argv) > 1 else "."
    print(f"Normalizing source file timestamps in {repo_root}...")

    cmd = ["git", "log", "--name-only", "--pretty=format:%ct"]
    try:
        proc = subprocess.Popen(cmd, cwd=repo_root, stdout=subprocess.PIPE, text=True)
    except Exception as e:
        print(f"Failed to run git log: {e}", file=sys.stderr)
        return

    cur_time = None
    seen_files = set()
    count = 0

    if proc.stdout is not None:
        for line in proc.stdout:
            line = line.strip()
            if not line:
                continue
            if line.isdigit():
                cur_time = int(line)
            elif cur_time and line not in seen_files:
                seen_files.add(line)
                full_path = os.path.join(repo_root, line)
                if os.path.isfile(full_path):
                    try:
                        os.utime(full_path, (cur_time, cur_time))
                        count += 1
                    except OSError:
                        pass

    # For shallow clones, set remaining files to the earliest timestamp found (or safe past timestamp)
    fallback_time = cur_time if cur_time is not None else 1700000000
    for root, _, files in os.walk(repo_root):
        if ".git" in root.split(os.sep):
            continue
        for f in files:
            full_path = os.path.join(root, f)
            rel_path = os.path.relpath(full_path, repo_root)
            if rel_path not in seen_files:
                try:
                    os.utime(full_path, (fallback_time, fallback_time))
                    count += 1
                except OSError:
                    pass

    print(f"Successfully normalized timestamps for {count} source files.")

if __name__ == "__main__":
    main()
