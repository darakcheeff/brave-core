/* Copyright (c) 2026 The Brave Authors. All rights reserved.
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at https://mozilla.org/MPL/2.0/. */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

/**
 * redirect_cc wrapper for Clang / Clang++ when building with Ninja.
 * Emulates Brave's Siso redirect_cc handler by intercepting compilation commands
 * (-c <source>) and checking if an override file exists in brave/chromium_src/.
 * If found, substitutes the source file argument with the override file path
 * before delegating to the real Clang compiler.
 */
int main(int argc, char* argv[]) {
    char real_compiler[1024];
    snprintf(real_compiler, sizeof(real_compiler), "%s.real", argv[0]);

    char** new_argv = malloc((argc + 1) * sizeof(char*));
    if (!new_argv) {
        perror("malloc");
        return 1;
    }
    // Retain original argv[0] so Clang properly detects driver mode (clang vs clang++)
    new_argv[0] = argv[0];

    int debug = (getenv("REDIRECT_CC_DEBUG") != NULL);

    for (int i = 1; i < argc; i++) {
        new_argv[i] = argv[i];
    }
    new_argv[argc] = NULL;

    for (int i = 1; i < argc - 1; i++) {
        if (strcmp(argv[i], "-c") == 0 || strcmp(argv[i], "/c") == 0) {
            const char* src = argv[i + 1];
            char override[1024] = {0};

            if (strncmp(src, "../../", 6) == 0 && strncmp(src, "../../brave/", 14) != 0) {
                snprintf(override, sizeof(override), "../../brave/chromium_src/%s", src + 6);
            } else if (strncmp(src, "gen/", 4) == 0) {
                snprintf(override, sizeof(override), "../../brave/chromium_src/%s", src + 4);
            } else if (src[0] == '/') {
                const char* src_dir = strstr(src, "/src/");
                if (src_dir && strncmp(src_dir, "/src/brave/", 11) != 0) {
                    int prefix_len = (int)(src_dir - src) + 5;
                    snprintf(override, sizeof(override), "%.*sbrave/chromium_src/%s",
                             prefix_len, src, src_dir + 5);
                }
            } else {
                const char* gen = strstr(src, "/gen/");
                if (gen) {
                    snprintf(override, sizeof(override), "../../brave/chromium_src/%s", gen + 5);
                }
            }

            if (override[0] != '\0' && access(override, F_OK) == 0) {
                if (debug) {
                    fprintf(stderr, "[redirect_cc] Redirected %s -> %s\n", src, override);
                }
                new_argv[i + 1] = strdup(override);
            }
            break;
        }
    }

    execvp(real_compiler, new_argv);
    perror("execvp failed");
    return 1;
}
