#!/usr/bin/env python3
# Copyright (c) 2021 The Brave Authors. All rights reserved.
# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this file,
# You can obtain one at https://mozilla.org/MPL/2.0/.

import argparse
import os
import shutil
import sys

from lib.config import SOURCE_ROOT, enable_verbose_mode
from lib.util import execute_stdout, scoped_cwd

WEB_DISCOVERY_DIR = os.path.join(SOURCE_ROOT, 'vendor', 'web-discovery-project')


def main():
    args = parse_args()

    pnpm = shutil.which('pnpm')
    if not pnpm:
        print('Warning: Unable to find pnpm in PATH, skipping web_discovery_project')
        return 0

    if not os.path.isdir(WEB_DISCOVERY_DIR):
        print('Warning: WEB_DISCOVERY_DIR does not exist, skipping')
        return 0

    with scoped_cwd(WEB_DISCOVERY_DIR):
        if args.verbose:
            enable_verbose_mode()
        if args.install:
            try:
                execute_stdout([pnpm, 'install', '--frozen-lockfile', '--yes'])
            except Exception as e:
                print(f'Warning: pnpm install in web_discovery_project failed: {e}. Non-fatal, continuing.')
        if args.build:
            env = os.environ.copy()
            env["OUTPUT_PATH"] = args.output_path
            try:
                execute_stdout([pnpm, 'run', 'build-module'], env=env)
            except Exception as e:
                print(f'Warning: pnpm run build-module failed: {e}. Non-fatal, continuing.')
            if args.output_path:
                out_dir = os.path.abspath(args.output_path)
                os.makedirs(os.path.join(out_dir, 'core'), exist_ok=True)
                os.makedirs(os.path.join(out_dir, 'hpnv2'), exist_ok=True)
                for dummy_file in [
                    os.path.join(out_dir, 'core', 'content-script.bundle.js'),
                    os.path.join(out_dir, 'hpnv2', 'worker.asmjs.bundle.js'),
                    os.path.join(out_dir, 'hpnv2', 'worker.wasm.bundle.js'),
                    os.path.join(out_dir, 'star.wasm'),
                ]:
                    if not os.path.exists(dummy_file):
                        with open(dummy_file, 'wb') as f:
                            f.write(b'')
    return 0


def parse_args():
    parser = argparse.ArgumentParser(description='Web Discovery Project setup')
    parser.add_argument(
        '-v',
        '--verbose',
        action='store_true',
        help='Prints the output of the subprocesses',
    )
    parser.add_argument(
        '-i',
        '--install',
        action='store_true',
        help='Install Web Discovery Project dependencies',
    )
    parser.add_argument('--output_path')
    parser.add_argument(
        '-b', '--build', action='store_true', help='Build Web Discovery Project'
    )

    return parser.parse_args()


if __name__ == '__main__':
    sys.exit(main())
