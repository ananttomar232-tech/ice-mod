#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
build_fabric_mod.py - generates the complete "Ice Age Yantra" FABRIC 1.20.1 mod project (a port of the Forge mod).

  python3 build_fabric_mod.py            # writes ./ice_age_yantra_fabric
  cd ice_age_yantra_fabric && ./gradlew build
  -> build/libs/ice_age_yantra-1.0.0.jar   (put it into .minecraft/mods together with Fabric API)

It is a PORT: the Fabric mod uses exactly the same assets as the Forge mod - the baked OBJ rigs made from the five
Sketchfab models (gola machine, table fan, fridge, split AC, ice cream van), the portal frame, pedestal and hairdryer,
all 512px textures, the HUD counter bar, particles and sounds. Those are produced by the Forge generator
(build_ice_age_mod.py), so this script needs ONE of:
  * a generated Forge project next to it (./ice_age_yantra, committed in the repository)   [default, instant]
  * build_ice_age_mod.py + ./source_models next to it (it is run automatically, about 1.5 minutes)
  * --assets-from DIR pointing at a generated Forge project

Written by this script: build.gradle (Fabric Loom 1.7.4, yarn 1.20.1+build.10, Fabric API 0.92.2+1.20.1),
fabric.mod.json, the Gradle wrapper, all Java sources (an OBJ loader for Fabric, block entity / entity / item
renderers, HUD; ZERO mixins, so it is compatible with Sodium, Lithium, Iris and Replay Mod), blockstates, item models,
lang and loot tables.

Requirements: Python 3.8+ (numpy + Pillow only if the Forge generator has to be run), JDK 17+.
Options: --out DIR, --clean, --assets-from DIR, --forge-generator FILE, --models DIR.
"""
import argparse
import base64
import json
import os
import shutil
import stat
import subprocess
import sys
import tempfile
import types
import zlib

HERE = os.path.dirname(os.path.abspath(__file__))


def B64(s):
    return base64.b64decode(s).decode('utf-8')


def _load_module(name, src):
    mod = types.ModuleType(name)
    sys.modules[name] = mod
    exec(compile(src, name + '.py', 'exec'), mod.__dict__)
    return mod


_load_module('assetgen', r'''# JSON glue for the Fabric port. The heavy assets (OBJ rigs, 512px textures, HUD textures, sounds) come from the Forge
# generator (build_ice_age_mod.py): the Fabric mod uses exactly the same models and textures.
import json
import os

MODID = 'ice_age_yantra'

YANTRA_BLOCKS = ['gola_machine', 'farrata_fan', 'retro_fridge', 'ac_unit']
PARTICLE_TEX = {'gola_machine': 'gola_enamel', 'farrata_fan': 'fan_body', 'retro_fridge': 'fridge_main', 'ac_unit': 'ac_body',
                'village_portal': 'portal_crystal', 'exit_portal': 'portal_crystal', 'showcase': 'pedestal_ice'}

LANG = {
    'itemGroup.ice_age_yantra': 'Ice Age Yantra',
    'block.ice_age_yantra.gola_machine': 'Desi Baraf Gola Machine',
    'block.ice_age_yantra.farrata_fan': 'Farrata High-Speed Table Fan',
    'block.ice_age_yantra.retro_fridge': 'Retro Single-Door Fridge',
    'block.ice_age_yantra.ac_unit': 'Split AC Unit',
    'block.ice_age_yantra.village_portal': 'Village Portal',
    'block.ice_age_yantra.exit_portal': 'Exit Portal',
    'block.ice_age_yantra.showcase': 'Ice Shop Pedestal',
    'item.ice_age_yantra.ice_cream_van': 'Musical Ice Cream Van',
    'item.ice_age_yantra.gotiya_spawn_egg': 'Gotiya Spawn Egg',
    'entity.ice_age_yantra.ice_cream_van': 'Musical Ice Cream Van',
    'entity.ice_age_yantra.gotiya': 'Gotiya',
}
SOUND_NAMES = ['van_jingle', 'van_honk', 'handle_pop', 'crank', 'wheel_spin', 'sputter', 'squeak', 'fan_whoosh', 'fan_roar', 'fridge_open',
               'fridge_slam', 'ac_blizzard', 'remote_beep', 'remote_standby', 'ding', 'freeze', 'purchase', 'target_reached', 'fanfare',
               'portal_whoosh']
for _n in SOUND_NAMES:
    LANG['subtitles.ice_age_yantra.' + _n] = _n.replace('_', ' ').capitalize()


def _json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8') as f:
        json.dump(obj, f, indent=2)
        f.write('\n')


def loot(item, lower_only=False):
    cond = [{'condition': 'minecraft:survives_explosion'}]
    if lower_only:
        cond.insert(0, {'condition': 'minecraft:block_state_property', 'block': '%s:village_portal' % MODID, 'properties': {'half': 'lower'}})
    return {'type': 'minecraft:block', 'pools': [{'rolls': 1.0, 'bonus_rolls': 0.0,
                                                  'entries': [{'type': 'minecraft:item', 'name': '%s:%s' % (MODID, item)}],
                                                  'conditions': cond}]}


def generate(out, item_display):
    A = os.path.join(out, 'src', 'main', 'resources', 'assets', MODID)
    D = os.path.join(out, 'src', 'main', 'resources', 'data', MODID)
    # blocks are drawn by block entity renderers with the baked OBJ rigs: the block model only supplies the break particle
    for name, tex in PARTICLE_TEX.items():
        _json(os.path.join(A, 'models/block/%s.json' % name), {'textures': {'particle': '%s:block/%s' % (MODID, tex)}})
        _json(os.path.join(A, 'blockstates/%s.json' % name), {'multipart': [{'apply': {'model': '%s:block/%s' % (MODID, name)}}]})
    # items: the full 3D rig is drawn by the built-in item renderer, display transforms come from the Forge item models
    for item, disp in (('gola_machine', 'gola_machine'), ('farrata_fan', 'farrata_fan'), ('retro_fridge', 'retro_fridge'),
                       ('ac_unit', 'ac_unit'), ('ice_cream_van', 'ice_cream_van'), ('village_portal', 'ice_portal')):
        _json(os.path.join(A, 'models/item/%s.json' % item), {'parent': 'builtin/entity', 'gui_light': 'side', 'display': item_display[disp]})
    _json(os.path.join(A, 'models/item/gotiya_spawn_egg.json'), {'parent': 'minecraft:item/template_spawn_egg'})
    _json(os.path.join(A, 'lang/en_us.json'), LANG)
    for item in YANTRA_BLOCKS:
        _json(os.path.join(D, 'loot_tables/blocks/%s.json' % item), loot(item))
    _json(os.path.join(D, 'loot_tables/blocks/village_portal.json'), loot('village_portal', True))
''')

ITEM_DISPLAY = '{"gola_machine": {"gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.625, 0.625, 0.625]}, "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.25, 0.25, 0.25]}, "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.5, 0.5, 0.5]}, "head": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]}, "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]}, "thirdperson_lefthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]}, "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]}, "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]}}, "farrata_fan": {"gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.625, 0.625, 0.625]}, "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.25, 0.25, 0.25]}, "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.5, 0.5, 0.5]}, "head": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]}, "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]}, "thirdperson_lefthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]}, "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]}, "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]}}, "retro_fridge": {"gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.625, 0.625, 0.625]}, "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.25, 0.25, 0.25]}, "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.5, 0.5, 0.5]}, "head": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]}, "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]}, "thirdperson_lefthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]}, "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]}, "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]}}, "ac_unit": {"gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.625, 0.625, 0.625]}, "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.25, 0.25, 0.25]}, "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.5, 0.5, 0.5]}, "head": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]}, "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]}, "thirdperson_lefthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]}, "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]}, "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]}}, "ice_cream_van": {"gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.625, 0.625, 0.625]}, "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.25, 0.25, 0.25]}, "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.5, 0.5, 0.5]}, "head": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]}, "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]}, "thirdperson_lefthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]}, "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]}, "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]}}, "ice_portal": {"gui": {"rotation": [20, 200, 0], "translation": [0, -2.5, 0], "scale": [0.36, 0.36, 0.36]}, "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.2, 0.2, 0.2]}, "fixed": {"rotation": [0, 180, 0], "translation": [0, -3, 0], "scale": [0.36, 0.36, 0.36]}, "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.25, 0.25, 0.25]}, "thirdperson_lefthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.25, 0.25, 0.25]}, "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.25, 0.25, 0.25]}, "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 0, 0], "scale": [0.25, 0.25, 0.25]}}}'

FILES = {
    'LICENSE': ('text', r'''MIT License - Ice Age Yantra
'''),
    'build.gradle': ('text', r'''plugins {
    id 'fabric-loom' version '1.7.4'
    id 'maven-publish'
}

version = project.mod_version
group = project.maven_group

base {
    archivesName = project.archives_base_name
}

repositories {
    // Fabric API and Minecraft libraries are resolved by Loom; nothing else is needed.
}

dependencies {
    minecraft "com.mojang:minecraft:${project.minecraft_version}"
    mappings "net.fabricmc:yarn:${project.yarn_mappings}:v2"
    modImplementation "net.fabricmc:fabric-loader:${project.loader_version}"
    modImplementation "net.fabricmc.fabric-api:fabric-api:${project.fabric_version}"
}

processResources {
    inputs.property "version", project.version
    filesMatching("fabric.mod.json") {
        expand "version": project.version
    }
}

tasks.withType(JavaCompile).configureEach {
    it.options.release = 17
    it.options.encoding = 'UTF-8'
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    withSourcesJar()
}

jar {
    from("LICENSE") {
        rename { "${it}_${project.base.archivesName.get()}" }
    }
}
'''),
    'gradle.properties': ('text', r'''org.gradle.jvmargs=-Xmx2G
org.gradle.parallel=true
minecraft_version=1.20.1
yarn_mappings=1.20.1+build.10
loader_version=0.15.11
fabric_version=0.92.2+1.20.1
mod_version=1.0.0
maven_group=com.iceage
archives_base_name=ice_age_yantra
'''),
    'gradle/wrapper/gradle-wrapper.jar': ('bin', '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'),
    'gradle/wrapper/gradle-wrapper.properties': ('text', r'''distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.8-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
'''),
    'gradlew': ('text', r'''#!/bin/sh

#
# Copyright © 2015-2021 the original authors.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

##############################################################################
#
#   Gradle start up script for POSIX generated by Gradle.
#
#   Important for running:
#
#   (1) You need a POSIX-compliant shell to run this script. If your /bin/sh is
#       noncompliant, but you have some other compliant shell such as ksh or
#       bash, then to run this script, type that shell name before the whole
#       command line, like:
#
#           ksh Gradle
#
#       Busybox and similar reduced shells will NOT work, because this script
#       requires all of these POSIX shell features:
#         * functions;
#         * expansions «$var», «${var}», «${var:-default}», «${var+SET}»,
#           «${var#prefix}», «${var%suffix}», and «$( cmd )»;
#         * compound commands having a testable exit status, especially «case»;
#         * various built-in commands including «command», «set», and «ulimit».
#
#   Important for patching:
#
#   (2) This script targets any POSIX shell, so it avoids extensions provided
#       by Bash, Ksh, etc; in particular arrays are avoided.
#
#       The "traditional" practice of packing multiple parameters into a
#       space-separated string is a well documented source of bugs and security
#       problems, so this is (mostly) avoided, by progressively accumulating
#       options in "$@", and eventually passing that to Java.
#
#       Where the inherited environment variables (DEFAULT_JVM_OPTS, JAVA_OPTS,
#       and GRADLE_OPTS) rely on word-splitting, this is performed explicitly;
#       see the in-line comments for details.
#
#       There are tweaks for specific operating systems such as AIX, CygWin,
#       Darwin, MinGW, and NonStop.
#
#   (3) This script is generated from the Groovy template
#       https://github.com/gradle/gradle/blob/HEAD/subprojects/plugins/src/main/resources/org/gradle/api/internal/plugins/unixStartScript.txt
#       within the Gradle project.
#
#       You can find Gradle at https://github.com/gradle/gradle/.
#
##############################################################################

# Attempt to set APP_HOME

# Resolve links: $0 may be a link
app_path=$0

# Need this for daisy-chained symlinks.
while
    APP_HOME=${app_path%"${app_path##*/}"}  # leaves a trailing /; empty if no leading path
    [ -h "$app_path" ]
do
    ls=$( ls -ld "$app_path" )
    link=${ls#*' -> '}
    case $link in             #(
      /*)   app_path=$link ;; #(
      *)    app_path=$APP_HOME$link ;;
    esac
done

# This is normally unused
# shellcheck disable=SC2034
APP_BASE_NAME=${0##*/}
APP_HOME=$( cd "${APP_HOME:-./}" && pwd -P ) || exit

# Use the maximum available, or set MAX_FD != -1 to use that value.
MAX_FD=maximum

warn () {
    echo "$*"
} >&2

die () {
    echo
    echo "$*"
    echo
    exit 1
} >&2

# OS specific support (must be 'true' or 'false').
cygwin=false
msys=false
darwin=false
nonstop=false
case "$( uname )" in                #(
  CYGWIN* )         cygwin=true  ;; #(
  Darwin* )         darwin=true  ;; #(
  MSYS* | MINGW* )  msys=true    ;; #(
  NONSTOP* )        nonstop=true ;;
esac

CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar


# Determine the Java command to use to start the JVM.
if [ -n "$JAVA_HOME" ] ; then
    if [ -x "$JAVA_HOME/jre/sh/java" ] ; then
        # IBM's JDK on AIX uses strange locations for the executables
        JAVACMD=$JAVA_HOME/jre/sh/java
    else
        JAVACMD=$JAVA_HOME/bin/java
    fi
    if [ ! -x "$JAVACMD" ] ; then
        die "ERROR: JAVA_HOME is set to an invalid directory: $JAVA_HOME

Please set the JAVA_HOME variable in your environment to match the
location of your Java installation."
    fi
else
    JAVACMD=java
    which java >/dev/null 2>&1 || die "ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.

Please set the JAVA_HOME variable in your environment to match the
location of your Java installation."
fi

# Increase the maximum file descriptors if we can.
if ! "$cygwin" && ! "$darwin" && ! "$nonstop" ; then
    case $MAX_FD in #(
      max*)
        # In POSIX sh, ulimit -H is undefined. That's why the result is checked to see if it worked.
        # shellcheck disable=SC3045
        MAX_FD=$( ulimit -H -n ) ||
            warn "Could not query maximum file descriptor limit"
    esac
    case $MAX_FD in  #(
      '' | soft) :;; #(
      *)
        # In POSIX sh, ulimit -n is undefined. That's why the result is checked to see if it worked.
        # shellcheck disable=SC3045
        ulimit -n "$MAX_FD" ||
            warn "Could not set maximum file descriptor limit to $MAX_FD"
    esac
fi

# Collect all arguments for the java command, stacking in reverse order:
#   * args from the command line
#   * the main class name
#   * -classpath
#   * -D...appname settings
#   * --module-path (only if needed)
#   * DEFAULT_JVM_OPTS, JAVA_OPTS, and GRADLE_OPTS environment variables.

# For Cygwin or MSYS, switch paths to Windows format before running java
if "$cygwin" || "$msys" ; then
    APP_HOME=$( cygpath --path --mixed "$APP_HOME" )
    CLASSPATH=$( cygpath --path --mixed "$CLASSPATH" )

    JAVACMD=$( cygpath --unix "$JAVACMD" )

    # Now convert the arguments - kludge to limit ourselves to /bin/sh
    for arg do
        if
            case $arg in                                #(
              -*)   false ;;                            # don't mess with options #(
              /?*)  t=${arg#/} t=/${t%%/*}              # looks like a POSIX filepath
                    [ -e "$t" ] ;;                      #(
              *)    false ;;
            esac
        then
            arg=$( cygpath --path --ignore --mixed "$arg" )
        fi
        # Roll the args list around exactly as many times as the number of
        # args, so each arg winds up back in the position where it started, but
        # possibly modified.
        #
        # NB: a `for` loop captures its iteration list before it begins, so
        # changing the positional parameters here affects neither the number of
        # iterations, nor the values presented in `arg`.
        shift                   # remove old arg
        set -- "$@" "$arg"      # push replacement arg
    done
fi


# Add default JVM options here. You can also use JAVA_OPTS and GRADLE_OPTS to pass JVM options to this script.
DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'

# Collect all arguments for the java command;
#   * $DEFAULT_JVM_OPTS, $JAVA_OPTS, and $GRADLE_OPTS can contain fragments of
#     shell script including quotes and variable substitutions, so put them in
#     double quotes to make sure that they get re-expanded; and
#   * put everything else in single quotes, so that it's not re-expanded.

set -- \
        "-Dorg.gradle.appname=$APP_BASE_NAME" \
        -classpath "$CLASSPATH" \
        org.gradle.wrapper.GradleWrapperMain \
        "$@"

# Stop when "xargs" is not available.
if ! command -v xargs >/dev/null 2>&1
then
    die "xargs is not available"
fi

# Use "xargs" to parse quoted args.
#
# With -n1 it outputs one arg per line, with the quotes and backslashes removed.
#
# In Bash we could simply go:
#
#   readarray ARGS < <( xargs -n1 <<<"$var" ) &&
#   set -- "${ARGS[@]}" "$@"
#
# but POSIX shell has neither arrays nor command substitution, so instead we
# post-process each arg (as a line of input to sed) to backslash-escape any
# character that might be a shell metacharacter, then use eval to reverse
# that process (while maintaining the separation between arguments), and wrap
# the whole thing up as a single "set" statement.
#
# This will of course break if any of these variables contains a newline or
# an unmatched quote.
#

eval "set -- $(
        printf '%s\n' "$DEFAULT_JVM_OPTS $JAVA_OPTS $GRADLE_OPTS" |
        xargs -n1 |
        sed ' s~[^-[:alnum:]+,./:=@_]~\\&~g; ' |
        tr '\n' ' '
    )" '"$@"'

exec "$JAVACMD" "$@"
'''),
    'gradlew.bat': ('text', r'''@rem
@rem Copyright 2015 the original author or authors.
@rem
@rem Licensed under the Apache License, Version 2.0 (the "License");
@rem you may not use this file except in compliance with the License.
@rem You may obtain a copy of the License at
@rem
@rem      https://www.apache.org/licenses/LICENSE-2.0
@rem
@rem Unless required by applicable law or agreed to in writing, software
@rem distributed under the License is distributed on an "AS IS" BASIS,
@rem WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
@rem See the License for the specific language governing permissions and
@rem limitations under the License.
@rem

@if "%DEBUG%"=="" @echo off
@rem ##########################################################################
@rem
@rem  Gradle startup script for Windows
@rem
@rem ##########################################################################

@rem Set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" setlocal

set DIRNAME=%~dp0
if "%DIRNAME%"=="" set DIRNAME=.
@rem This is normally unused
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%

@rem Resolve any "." and ".." in APP_HOME to make it shorter.
for %%i in ("%APP_HOME%") do set APP_HOME=%%~fi

@rem Add default JVM options here. You can also use JAVA_OPTS and GRADLE_OPTS to pass JVM options to this script.
set DEFAULT_JVM_OPTS="-Xmx64m" "-Xms64m"

@rem Find java.exe
if defined JAVA_HOME goto findJavaFromJavaHome

set JAVA_EXE=java.exe
%JAVA_EXE% -version >NUL 2>&1
if %ERRORLEVEL% equ 0 goto execute

echo.
echo ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.
echo.
echo Please set the JAVA_HOME variable in your environment to match the
echo location of your Java installation.

goto fail

:findJavaFromJavaHome
set JAVA_HOME=%JAVA_HOME:"=%
set JAVA_EXE=%JAVA_HOME%/bin/java.exe

if exist "%JAVA_EXE%" goto execute

echo.
echo ERROR: JAVA_HOME is set to an invalid directory: %JAVA_HOME%
echo.
echo Please set the JAVA_HOME variable in your environment to match the
echo location of your Java installation.

goto fail

:execute
@rem Setup the command line

set CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar


@rem Execute Gradle
"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %GRADLE_OPTS% "-Dorg.gradle.appname=%APP_BASE_NAME%" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*

:end
@rem End local scope for the variables with windows NT shell
if %ERRORLEVEL% equ 0 goto mainEnd

:fail
rem Set variable GRADLE_EXIT_CONSOLE if you need the _script_ return code instead of
rem the _cmd.exe /c_ return code!
set EXIT_CODE=%ERRORLEVEL%
if %EXIT_CODE% equ 0 set EXIT_CODE=1
if not ""=="%GRADLE_EXIT_CONSOLE%" exit %EXIT_CODE%
exit /b %EXIT_CODE%

:mainEnd
if "%OS%"=="Windows_NT" endlocal

:omega
'''),
    'settings.gradle': ('text', r'''pluginManagement {
    repositories {
        maven { url = 'https://maven.fabricmc.net/' }
        mavenCentral()
        gradlePluginPortal()
    }
}
rootProject.name = 'ice_age_yantra'
'''),
    'src/main/java/com/iceage/yantra/Commands.java': ('text', r'''package com.iceage.yantra;

import com.iceage.yantra.block.PortalBlock;
import com.iceage.yantra.entity.GotiyaEntity;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/**
 * /iceage portal | shop | setup | rebuildshop | counter reset|set|speed | gotiya.
 * Commands never print chat messages (the screen stays clean).
 */
public final class Commands {
    private Commands() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> d, CommandRegistryAccess access, CommandManager.RegistrationEnvironment env) {
        d.register(CommandManager.literal("iceage")
                .then(CommandManager.literal("portal").requires(s -> s.hasPermissionLevel(2)).executes(c -> {
                    placePortal(c.getSource().getPlayerOrThrow());
                    return 1;
                }))
                .then(CommandManager.literal("shop").requires(s -> s.hasPermissionLevel(2)).executes(c -> {
                    ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
                    ShopBuilder.ensureBuilt(p.server);
                    ShopPortals.teleport(p, p.server.getOverworld(), ShopBuilder.ARRIVAL, 0f, 5f);
                    return 1;
                }))
                .then(CommandManager.literal("rebuildshop").requires(s -> s.hasPermissionLevel(2)).executes(c -> {
                    ShopBuilder.rebuild(c.getSource().getServer());
                    return 1;
                }))
                .then(CommandManager.literal("setup").requires(s -> s.hasPermissionLevel(2)).executes(c -> {
                    ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
                    give(p, new ItemStack(ModItems.VILLAGE_PORTAL));
                    give(p, new ItemStack(ModItems.GOLA_MACHINE));
                    give(p, new ItemStack(ModItems.FARRATA_FAN));
                    give(p, new ItemStack(ModItems.RETRO_FRIDGE));
                    give(p, new ItemStack(ModItems.AC_UNIT));
                    give(p, new ItemStack(ModItems.ICE_CREAM_VAN));
                    return 1;
                }))
                .then(CommandManager.literal("counter").requires(s -> s.hasPermissionLevel(2))
                        .then(CommandManager.literal("reset").executes(c -> setCounter(c.getSource().getPlayerOrThrow(), 0L)))
                        .then(CommandManager.literal("set").then(CommandManager.argument("amount", LongArgumentType.longArg(0, Tier.FINAL_TARGET))
                                .executes(c -> setCounter(c.getSource().getPlayerOrThrow(), LongArgumentType.getLong(c, "amount")))))
                        .then(CommandManager.literal("speed").then(CommandManager.argument("multiplier", FloatArgumentType.floatArg(0.1f, 1000f))
                                .executes(c -> {
                                    ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
                                    IcePoints.setSpeed(p.server, p.getUuid(), FloatArgumentType.getFloat(c, "multiplier"));
                                    return 1;
                                }))))
                .then(CommandManager.literal("gotiya").requires(s -> s.hasPermissionLevel(2)).executes(c -> {
                    ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
                    Vec3d look = p.getRotationVec(1f).multiply(1, 0, 1).normalize();
                    Vec3d at = p.getPos().add(look.multiply(16));
                    GotiyaEntity.spawnClimax((ServerWorld) p.getWorld(), at, p);
                    return 1;
                })));
    }

    private static int setCounter(ServerPlayerEntity p, long v) {
        IcePoints.set(p.server, p.getUuid(), v);
        return 1;
    }

    private static void give(ServerPlayerEntity p, ItemStack s) {
        if (!p.getInventory().insertStack(s)) p.dropItem(s, false);
    }

    /** Places a Village Portal three blocks in front of the player, facing them. */
    private static void placePortal(ServerPlayerEntity p) throws CommandSyntaxException {
        ServerWorld w = (ServerWorld) p.getWorld();
        Direction look = p.getHorizontalFacing();
        BlockPos at = p.getBlockPos().offset(look, 3);
        Vec3d ground = ShopPortals.groundAt(w, new Vec3d(at.getX() + 0.5, p.getY(), at.getZ() + 0.5));
        BlockPos base = BlockPos.ofFloored(ground);
        BlockState lower = ModBlocks.VILLAGE_PORTAL.getDefaultState().with(PortalBlock.FACING, look.getOpposite()).with(PortalBlock.HALF, DoubleBlockHalf.LOWER);
        w.setBlockState(base, lower, Block.NOTIFY_ALL);
        w.setBlockState(base.up(), lower.with(PortalBlock.HALF, DoubleBlockHalf.UPPER), Block.NOTIFY_ALL);
    }
}
'''),
    'src/main/java/com/iceage/yantra/FreezeEngine.java': ('text', r'''package com.iceage.yantra;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FluidBlock;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/**
 * Freezing and melting of terrain. Only packed ice and blue ice are ever produced (neither melts by itself near
 * light, fire or sun). Only Gotiya's hairdryer melts them back.
 */
public final class FreezeEngine {
    private static final int FLAGS = Block.NOTIFY_LISTENERS | Block.FORCE_STATE;

    private FreezeEngine() {
    }

    public static boolean isFrozen(BlockState s) {
        return s.isOf(Blocks.PACKED_ICE) || s.isOf(Blocks.BLUE_ICE) || s.isOf(Blocks.ICE);
    }

    /** Blocks that must never be frozen or melted: unbreakable, block entities, this mod's blocks and the shop room. */
    public static boolean isProtected(ServerWorld w, BlockPos pos, BlockState s) {
        if (ShopBuilder.isInShopZone(pos)) return true;
        if (s.getHardness(w, pos) < 0) return true;
        if (s.hasBlockEntity()) return true;
        return Registries.BLOCK.getId(s.getBlock()).getNamespace().equals(IceAgeYantra.MODID);
    }

    private static BlockState iceFor(ServerWorld w, BlockPos pos) {
        return w.random.nextInt(5) == 0 ? Blocks.BLUE_ICE.getDefaultState() : Blocks.PACKED_ICE.getDefaultState();
    }

    /**
     * Freezes the topmost solid (or water) block of the column x/z found between yRef+up and yRef-down.
     * Plants and snow on top are cleared. Returns the number of blocks that changed.
     */
    public static int freezeColumn(ServerWorld w, int x, int z, int yRef, int up, int down) {
        if (!w.isChunkLoaded(x >> 4, z >> 4)) return 0;
        BlockPos.Mutable m = new BlockPos.Mutable();
        int changed = 0;
        for (int y = yRef + up; y >= yRef - down; y--) {
            m.set(x, y, z);
            BlockState s = w.getBlockState(m);
            if (s.isAir()) continue;
            if (isFrozen(s)) return changed;
            boolean fluid = !s.getFluidState().isEmpty();
            if (!fluid && s.isReplaceable()) {
                // grass, flowers, snow layers: clear away and keep looking for the ground below
                if (!isProtected(w, m, s)) {
                    w.setBlockState(m, Blocks.AIR.getDefaultState(), FLAGS);
                    changed++;
                }
                continue;
            }
            if (isProtected(w, m, s)) return changed;
            w.setBlockState(m, iceFor(w, m), FLAGS);
            return changed + 1;
        }
        return changed;
    }

    /** Paves a road: like freezeColumn but also fills empty space right under the vehicle. */
    public static int paveColumn(ServerWorld w, int x, int z, int roadY) {
        if (!w.isChunkLoaded(x >> 4, z >> 4)) return 0;
        BlockPos road = new BlockPos(x, roadY, z);
        BlockState s = w.getBlockState(road);
        if (s.isAir() || (!s.getFluidState().isEmpty()) || (s.isReplaceable())) {
            if (!isProtected(w, road, s) || s.isAir()) {
                w.setBlockState(road, Blocks.PACKED_ICE.getDefaultState(), FLAGS);
                return 1;
            }
            return 0;
        }
        return freezeColumn(w, x, z, roadY, 0, 1);
    }

    /** Hairdryer: turns packed/blue ice of a column back into (briefly flowing) water, leaving the ground below. */
    public static int meltColumn(ServerWorld w, int x, int z, int yRef) {
        if (!w.isChunkLoaded(x >> 4, z >> 4)) return 0;
        BlockPos.Mutable m = new BlockPos.Mutable();
        int changed = 0;
        for (int y = yRef + 2; y >= yRef - 3; y--) {
            m.set(x, y, z);
            BlockState s = w.getBlockState(m);
            if (!isFrozen(s) || ShopBuilder.isInShopZone(m)) continue;
            BlockState water = Blocks.WATER.getDefaultState().with(FluidBlock.LEVEL, 3);
            w.setBlockState(m, water, Block.NOTIFY_ALL);
            changed++;
        }
        return changed;
    }
}
'''),
    'src/main/java/com/iceage/yantra/IceAgeYantra.java': ('text', r'''package com.iceage.yantra;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

/** Ice Age Yantra (Fabric). No mixins: fully compatible with Sodium, Lithium, Iris and Replay Mod. */
public class IceAgeYantra implements ModInitializer {
    public static final String MODID = "ice_age_yantra";
    public static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(MODID);

    @Override
    public void onInitialize() {
        ModSounds.init();
        ModParticles.init();
        ModBlocks.init();
        ModItems.init();
        ModEntities.init();
        CommandRegistrationCallback.EVENT.register(Commands::register);
        ServerTickEvents.END_SERVER_TICK.register(IcePoints::flush);
        // build the secret overworld shop once (it is rebuilt automatically if the world was edited)
        ServerLifecycleEvents.SERVER_STARTED.register(ShopBuilder::ensureBuilt);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity p = handler.player;
            IceData data = IceData.get(server);
            IceData.Entry e = data.entry(p.getUuid());
            if (!e.gotStarter) {
                e.gotStarter = true;
                data.markDirty();
                ItemStack portal = new ItemStack(ModItems.VILLAGE_PORTAL);
                if (!p.getInventory().insertStack(portal)) p.dropItem(portal, false);
            }
            IcePoints.send(p, 0L);
        });
    }
}
'''),
    'src/main/java/com/iceage/yantra/IceData.java': ('text', r'''package com.iceage.yantra;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** World-wide persistent data: per-player counter, counter speed, portal return position, shop build version. */
public class IceData extends PersistentState {
    public static final String KEY = "ice_age_yantra";
    /** NBT key of the saved portal return position (x, y, z, yaw). */
    public static final String RETURN_POS = "IcePortalReturnPos";

    public static class Entry {
        public long points;
        public float speed = 1.0f;
        public boolean gotStarter;
        public boolean hasReturn;
        public double rx, ry, rz;
        public float ryaw;

        NbtCompound save() {
            NbtCompound t = new NbtCompound();
            t.putLong("points", points);
            t.putFloat("speed", speed);
            t.putBoolean("starter", gotStarter);
            if (hasReturn) {
                NbtCompound r = new NbtCompound();
                r.putDouble("x", rx);
                r.putDouble("y", ry);
                r.putDouble("z", rz);
                r.putFloat("yaw", ryaw);
                t.put(RETURN_POS, r);
            }
            return t;
        }

        static Entry load(NbtCompound t) {
            Entry e = new Entry();
            e.points = t.getLong("points");
            e.speed = t.contains("speed") ? t.getFloat("speed") : 1.0f;
            e.gotStarter = t.getBoolean("starter");
            if (t.contains(RETURN_POS, NbtElement.COMPOUND_TYPE)) {
                NbtCompound r = t.getCompound(RETURN_POS);
                e.hasReturn = true;
                e.rx = r.getDouble("x");
                e.ry = r.getDouble("y");
                e.rz = r.getDouble("z");
                e.ryaw = r.getFloat("yaw");
            }
            return e;
        }
    }

    private final Map<UUID, Entry> entries = new HashMap<>();
    public int shopVersion;

    public static IceData get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(IceData::fromNbt, IceData::new, KEY);
    }

    public Entry entry(UUID id) {
        return entries.computeIfAbsent(id, k -> new Entry());
    }

    public static IceData fromNbt(NbtCompound tag) {
        IceData d = new IceData();
        d.shopVersion = tag.getInt("shopVersion");
        NbtList list = tag.getList("players", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound c = list.getCompound(i);
            if (c.containsUuid("id")) d.entries.put(c.getUuid("id"), Entry.load(c));
        }
        return d;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound tag) {
        tag.putInt("shopVersion", shopVersion);
        NbtList list = new NbtList();
        entries.forEach((id, e) -> {
            NbtCompound c = e.save();
            c.putUuid("id", id);
            list.add(c);
        });
        tag.put("players", list);
        return tag;
    }
}
'''),
    'src/main/java/com/iceage/yantra/IcePoints.java': ('text', r'''package com.iceage.yantra;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** The frozen-blocks counter: awarding, multiplier ("speed") and syncing to the client HUD. */
public final class IcePoints {
    private static final Map<UUID, Long> PENDING = new HashMap<>();

    private IcePoints() {
    }

    public static long get(MinecraftServer server, UUID id) {
        return IceData.get(server).entry(id).points;
    }

    public static float speed(MinecraftServer server, UUID id) {
        return IceData.get(server).entry(id).speed;
    }

    /** Adds frozen blocks (multiplied by the counter speed). Returns how many were really added. */
    public static long award(MinecraftServer server, UUID id, long raw) {
        if (server == null || id == null || raw <= 0) return 0;
        IceData data = IceData.get(server);
        IceData.Entry e = data.entry(id);
        long before = e.points;
        if (before >= Tier.FINAL_TARGET) return 0;
        long amount = Math.max(1L, Math.round(raw * (double) e.speed));
        long after = Math.min(Tier.FINAL_TARGET, before + amount);
        e.points = after;
        data.markDirty();
        PENDING.merge(id, after - before, Long::sum);
        return after - before;
    }

    public static void set(MinecraftServer server, UUID id, long value) {
        IceData data = IceData.get(server);
        data.entry(id).points = Math.max(0L, Math.min(Tier.FINAL_TARGET, value));
        data.markDirty();
        PENDING.merge(id, 0L, Long::sum);
    }

    public static void setSpeed(MinecraftServer server, UUID id, float speed) {
        IceData data = IceData.get(server);
        data.entry(id).speed = Math.max(0.1f, Math.min(1000f, speed));
        data.markDirty();
        PENDING.merge(id, 0L, Long::sum);
    }

    public static void send(ServerPlayerEntity p, long gained) {
        IceData.Entry e = IceData.get(p.server).entry(p.getUuid());
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeLong(e.points);
        buf.writeFloat(e.speed);
        buf.writeLong(gained);
        ServerPlayNetworking.send(p, Net.POINTS, buf);
    }

    /** Called at the end of every server tick: pushes changed counters to their owners (one packet per tick). */
    public static void flush(MinecraftServer server) {
        if (PENDING.isEmpty()) return;
        var it = PENDING.entrySet().iterator();
        while (it.hasNext()) {
            var en = it.next();
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(en.getKey());
            if (p != null) send(p, en.getValue());
            it.remove();
        }
    }
}
'''),
    'src/main/java/com/iceage/yantra/ModBlocks.java': ('text', r'''package com.iceage.yantra;

import com.iceage.yantra.block.ExitPortalBlock;
import com.iceage.yantra.block.PortalBlock;
import com.iceage.yantra.block.ShowcaseBlock;
import com.iceage.yantra.block.PortalBlockEntity;
import com.iceage.yantra.block.ShowcaseBlockEntity;
import com.iceage.yantra.block.YantraBlockEntity;
import com.iceage.yantra.block.YantraBlock;
import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;

public final class ModBlocks {
    private static FabricBlockSettings yantra() {
        return FabricBlockSettings.create().mapColor(MapColor.LIGHT_BLUE).strength(2.0f).nonOpaque().sounds(BlockSoundGroup.METAL);
    }

    public static final Block GOLA_MACHINE = register("gola_machine",
            new YantraBlock(yantra(), Tier.GOLA, Block.createCuboidShape(1, 0, 1, 15, 16, 15)));
    public static final Block FARRATA_FAN = register("farrata_fan",
            new YantraBlock(yantra(), Tier.FAN, Block.createCuboidShape(2, 0, 2, 14, 16, 14)));
    public static final Block RETRO_FRIDGE = register("retro_fridge",
            new YantraBlock(yantra(), Tier.FRIDGE, Block.createCuboidShape(2, 0, 1, 14, 16, 15)));
    public static final Block AC_UNIT = register("ac_unit",
            new YantraBlock(yantra(), Tier.AC, Block.createCuboidShape(0, 0, 1, 16, 15, 11)));
    public static final Block VILLAGE_PORTAL = register("village_portal", new PortalBlock(FabricBlockSettings.create()
            .mapColor(MapColor.LIGHT_BLUE).strength(2.0f).nonOpaque().noCollision().luminance(s -> 11).sounds(BlockSoundGroup.GLASS)));
    public static final Block EXIT_PORTAL = register("exit_portal", new ExitPortalBlock(FabricBlockSettings.create()
            .mapColor(MapColor.LIGHT_BLUE).strength(-1.0f, 3600000.0f).nonOpaque().noCollision().luminance(s -> 12).dropsNothing()
            .sounds(BlockSoundGroup.GLASS)));
    public static final Block SHOWCASE = register("showcase", new ShowcaseBlock(FabricBlockSettings.create()
            .mapColor(MapColor.LIGHT_BLUE).strength(-1.0f, 3600000.0f).nonOpaque().luminance(s -> 8).dropsNothing().sounds(BlockSoundGroup.GLASS)));

    public static BlockEntityType<ShowcaseBlockEntity> SHOWCASE_BE;
    public static BlockEntityType<YantraBlockEntity> YANTRA_BE;
    public static BlockEntityType<PortalBlockEntity> PORTAL_BE;

    private ModBlocks() {
    }

    private static Block register(String name, Block block) {
        return Registry.register(Registries.BLOCK, new Identifier(IceAgeYantra.MODID, name), block);
    }

    public static Block forTier(Tier t) {
        return switch (t) {
            case GOLA -> GOLA_MACHINE;
            case FAN -> FARRATA_FAN;
            case FRIDGE -> RETRO_FRIDGE;
            case AC -> AC_UNIT;
            case VAN -> SHOWCASE;
        };
    }

    public static void init() {
        YANTRA_BE = Registry.register(Registries.BLOCK_ENTITY_TYPE, new Identifier(IceAgeYantra.MODID, "yantra"),
                FabricBlockEntityTypeBuilder.create(YantraBlockEntity::new, GOLA_MACHINE, FARRATA_FAN, RETRO_FRIDGE, AC_UNIT).build());
        PORTAL_BE = Registry.register(Registries.BLOCK_ENTITY_TYPE, new Identifier(IceAgeYantra.MODID, "portal"),
                FabricBlockEntityTypeBuilder.create(PortalBlockEntity::new, VILLAGE_PORTAL, EXIT_PORTAL).build());
        SHOWCASE_BE = Registry.register(Registries.BLOCK_ENTITY_TYPE, new Identifier(IceAgeYantra.MODID, "showcase"),
                FabricBlockEntityTypeBuilder.create(ShowcaseBlockEntity::new, SHOWCASE).build());
    }
}
'''),
    'src/main/java/com/iceage/yantra/ModEntities.java': ('text', r'''package com.iceage.yantra;

import com.iceage.yantra.entity.GotiyaEntity;
import com.iceage.yantra.entity.IceVanEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class ModEntities {
    public static final EntityType<IceVanEntity> ICE_VAN = Registry.register(Registries.ENTITY_TYPE,
            new Identifier(IceAgeYantra.MODID, "ice_cream_van"),
            FabricEntityTypeBuilder.<IceVanEntity>create(SpawnGroup.MISC, IceVanEntity::new)
                    .dimensions(EntityDimensions.fixed(2.8f, 2.9f))
                    .trackRangeBlocks(160).trackedUpdateRate(1).forceTrackedVelocityUpdates(true).fireImmune().build());

    public static final EntityType<GotiyaEntity> GOTIYA = Registry.register(Registries.ENTITY_TYPE,
            new Identifier(IceAgeYantra.MODID, "gotiya"),
            FabricEntityTypeBuilder.<GotiyaEntity>create(SpawnGroup.CREATURE, GotiyaEntity::new)
                    .dimensions(EntityDimensions.fixed(0.6f, 1.95f))
                    .trackRangeBlocks(128).trackedUpdateRate(2).build());

    private ModEntities() {
    }

    public static void init() {
        FabricDefaultAttributeRegistry.register(GOTIYA, GotiyaEntity.createGotiyaAttributes());
    }
}
'''),
    'src/main/java/com/iceage/yantra/ModItems.java': ('text', r'''package com.iceage.yantra;

import com.iceage.yantra.item.IceVanItem;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public final class ModItems {
    public static final Item GOLA_MACHINE = block("gola_machine", ModBlocks.GOLA_MACHINE);
    public static final Item FARRATA_FAN = block("farrata_fan", ModBlocks.FARRATA_FAN);
    public static final Item RETRO_FRIDGE = block("retro_fridge", ModBlocks.RETRO_FRIDGE);
    public static final Item AC_UNIT = block("ac_unit", ModBlocks.AC_UNIT);
    public static final Item VILLAGE_PORTAL = block("village_portal", ModBlocks.VILLAGE_PORTAL);
    public static final Item ICE_CREAM_VAN = register("ice_cream_van", new IceVanItem(new FabricItemSettings().maxCount(1)));
    public static final Item GOTIYA_SPAWN_EGG = register("gotiya_spawn_egg",
            new SpawnEggItem(ModEntities.GOTIYA, 0xD8A878, 0x55E6FF, new FabricItemSettings()));

    private ModItems() {
    }

    private static Item block(String name, net.minecraft.block.Block block) {
        return register(name, new BlockItem(block, new FabricItemSettings()));
    }

    private static Item register(String name, Item item) {
        return Registry.register(Registries.ITEM, new Identifier(IceAgeYantra.MODID, name), item);
    }

    public static Item forTier(Tier t) {
        return switch (t) {
            case GOLA -> GOLA_MACHINE;
            case FAN -> FARRATA_FAN;
            case FRIDGE -> RETRO_FRIDGE;
            case AC -> AC_UNIT;
            case VAN -> ICE_CREAM_VAN;
        };
    }

    public static void init() {
        ItemGroup group = FabricItemGroup.builder()
                .icon(() -> new ItemStack(GOLA_MACHINE))
                .displayName(Text.translatable("itemGroup.ice_age_yantra"))
                .entries((ctx, entries) -> {
                    entries.add(VILLAGE_PORTAL);
                    entries.add(GOLA_MACHINE);
                    entries.add(FARRATA_FAN);
                    entries.add(RETRO_FRIDGE);
                    entries.add(AC_UNIT);
                    entries.add(ICE_CREAM_VAN);
                    entries.add(GOTIYA_SPAWN_EGG);
                }).build();
        Registry.register(Registries.ITEM_GROUP, new Identifier(IceAgeYantra.MODID, "main"), group);
    }
}
'''),
    'src/main/java/com/iceage/yantra/ModParticles.java': ('text', r'''package com.iceage.yantra;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.particle.DefaultParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/** The frost particle family of the Forge version (same textures). */
public final class ModParticles {
    public static final DefaultParticleType FROST_SWIRL = reg("frost_swirl");
    public static final DefaultParticleType ICE_SHARD = reg("ice_shard");
    public static final DefaultParticleType SNOWFLAKE = reg("snowflake");
    public static final DefaultParticleType FROST_MIST = reg("frost_mist");
    public static final DefaultParticleType SPARKLE = reg("sparkle");

    private ModParticles() {
    }

    private static DefaultParticleType reg(String name) {
        return Registry.register(Registries.PARTICLE_TYPE, new Identifier(IceAgeYantra.MODID, name), FabricParticleTypes.simple());
    }

    public static void init() {
    }
}
'''),
    'src/main/java/com/iceage/yantra/ModSounds.java': ('text', r'''package com.iceage.yantra;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

/** The sounds of the Forge version (same OGG files and sounds.json). */
public final class ModSounds {
    public static final SoundEvent VAN_JINGLE = sound("van_jingle");
    public static final SoundEvent VAN_HONK = sound("van_honk");
    public static final SoundEvent HANDLE_POP = sound("handle_pop");
    public static final SoundEvent CRANK = sound("crank");
    public static final SoundEvent WHEEL_SPIN = sound("wheel_spin");
    public static final SoundEvent SPUTTER = sound("sputter");
    public static final SoundEvent SQUEAK = sound("squeak");
    public static final SoundEvent FAN_WHOOSH = sound("fan_whoosh");
    public static final SoundEvent FAN_ROAR = sound("fan_roar");
    public static final SoundEvent FRIDGE_OPEN = sound("fridge_open");
    public static final SoundEvent FRIDGE_SLAM = sound("fridge_slam");
    public static final SoundEvent AC_BLIZZARD = sound("ac_blizzard");
    public static final SoundEvent REMOTE_BEEP = sound("remote_beep");
    public static final SoundEvent REMOTE_STANDBY = sound("remote_standby");
    public static final SoundEvent DING = sound("ding");
    public static final SoundEvent FREEZE = sound("freeze");
    public static final SoundEvent PURCHASE = sound("purchase");
    public static final SoundEvent TARGET_REACHED = sound("target_reached");
    public static final SoundEvent FANFARE = sound("fanfare");
    public static final SoundEvent PORTAL_WHOOSH = sound("portal_whoosh");

    private ModSounds() {
    }

    private static SoundEvent sound(String name) {
        Identifier id = new Identifier(IceAgeYantra.MODID, name);
        return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
    }

    public static void init() {
    }
}
'''),
    'src/main/java/com/iceage/yantra/Net.java': ('text', r'''package com.iceage.yantra;

import net.minecraft.util.Identifier;

/** Network channel ids. */
public final class Net {
    /** Server -> client: counter value, counter speed multiplier, gain since last packet. */
    public static final Identifier POINTS = new Identifier(IceAgeYantra.MODID, "points");

    private Net() {
    }
}
'''),
    'src/main/java/com/iceage/yantra/RigData.java': ('text', r'''package com.iceage.yantra;

/** Generated by build_ice_age_mod.py: pivots of the baked OBJ rigs (block units). */
public final class RigData {
    public static final double AC_ROTOR_X = 0.682712, AC_ROTOR_Y = 0.443117, AC_ROTOR_Z = 0.203421;
    public static final double FAN_NECK_X = 0.494654, FAN_NECK_Y = 0.924050, FAN_NECK_Z = 0.538675;
    public static final double FAN_ROTOR_X = 0.500000, FAN_ROTOR_Y = 0.858989, FAN_ROTOR_Z = 0.368640;
    public static final double FRIDGE_HINGE_X = 0.000000, FRIDGE_HINGE_Y = 0.000000, FRIDGE_HINGE_Z = 0.222000;
    public static final double GOLA_HANDLE_TOP_X = 0.500000, GOLA_HANDLE_TOP_Y = 1.200000, GOLA_HANDLE_TOP_Z = 0.667689;
    public static final double GOLA_WHEEL_X = 0.500082, GOLA_WHEEL_Y = 0.724775, GOLA_WHEEL_Z = 0.720986;
    public static final double TRUCK_CONE_X = 0.000000, TRUCK_CONE_Y = 2.062559, TRUCK_CONE_Z = -0.684000;
    public static final double TRUCK_SEAT_X = -0.345600, TRUCK_SEAT_Y = 0.262188, TRUCK_SEAT_Z = 0.806400;
    public static final double TRUCK_WHEEL_FL_X = 0.753412, TRUCK_WHEEL_FL_Y = 0.306144, TRUCK_WHEEL_FL_Z = 1.221382;
    public static final double TRUCK_WHEEL_FR_X = -0.753412, TRUCK_WHEEL_FR_Y = 0.306144, TRUCK_WHEEL_FR_Z = 1.221382;
    public static final double TRUCK_WHEEL_RL_X = 0.884783, TRUCK_WHEEL_RL_Y = 0.306144, TRUCK_WHEEL_RL_Z = -1.221383;
    public static final double TRUCK_WHEEL_RR_X = -0.884783, TRUCK_WHEEL_RR_Y = 0.306144, TRUCK_WHEEL_RR_Z = -1.221383;

    private RigData() {
    }
}
'''),
    'src/main/java/com/iceage/yantra/ShopBuilder.java': ('text', r'''package com.iceage.yantra;

import com.iceage.yantra.block.ShowcaseBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * The secret Ice Shop: a 15 x 8 x 15 room built in the OVERWORLD at X 10000 / Y 120 / Z 10000 (no custom dimension,
 * no chunk-loading lag). Packed ice, blue ice, glowstone lighting and glass display pedestals.
 */
public final class ShopBuilder {
    public static final int VERSION = 3;
    public static final int CX = 10000, CZ = 10000;
    public static final int FLOOR = 120, TOP = 127;
    public static final int X0 = CX - 7, X1 = CX + 7, Z0 = CZ - 7, Z1 = CZ + 7;
    /** Where players arrive: standing on the floor (top of the floor blocks = Y 121.0). */
    public static final Vec3d ARRIVAL = new Vec3d(10000.5, 121.0, 10000.5);
    /** Showcase blocks (tier = index + 1). Their glass pedestal stands one block below. */
    public static final BlockPos[] SHOWCASES = {
            new BlockPos(CX - 4, FLOOR + 1, CZ - 3), new BlockPos(CX + 4, FLOOR + 1, CZ - 3),
            new BlockPos(CX - 4, FLOOR + 1, CZ + 3), new BlockPos(CX + 4, FLOOR + 1, CZ + 3),
            new BlockPos(CX, FLOOR + 1, CZ + 4)
    };
    public static final int FLAGS = Block.NOTIFY_LISTENERS | Block.FORCE_STATE;

    private ShopBuilder() {
    }

    public static boolean isInShopZone(BlockPos p) {
        return p.getX() >= X0 - 4 && p.getX() <= X1 + 4 && p.getZ() >= Z0 - 4 && p.getZ() <= Z1 + 4
                && p.getY() >= FLOOR - 6 && p.getY() <= TOP + 4;
    }

    /** Builds the shop if it does not exist yet (or is an older version). Safe to call often. */
    public static void ensureBuilt(MinecraftServer server) {
        IceData data = IceData.get(server);
        ServerWorld w = server.getOverworld();
        boolean ok = data.shopVersion >= VERSION;
        if (ok) {
            // the world may have been edited: check one showcase
            ok = w.getBlockState(SHOWCASES[0]).isOf(ModBlocks.SHOWCASE);
        }
        if (ok) return;
        build(w);
        data.shopVersion = VERSION;
        data.markDirty();
    }

    public static void rebuild(MinecraftServer server) {
        build(server.getOverworld());
        IceData data = IceData.get(server);
        data.shopVersion = VERSION;
        data.markDirty();
    }

    private static void set(ServerWorld w, int x, int y, int z, BlockState s) {
        w.setBlockState(new BlockPos(x, y, z), s, FLAGS);
    }

    private static void build(ServerWorld w) {
        // make sure every chunk of the room is generated and loaded
        for (int cx = (X0 - 2) >> 4; cx <= (X1 + 2) >> 4; cx++) {
            for (int cz = (Z0 - 2) >> 4; cz <= (Z1 + 2) >> 4; cz++) {
                w.getChunk(cx, cz);
            }
        }
        BlockState air = Blocks.AIR.getDefaultState();
        BlockState packed = Blocks.PACKED_ICE.getDefaultState();
        BlockState blue = Blocks.BLUE_ICE.getDefaultState();
        BlockState glow = Blocks.GLOWSTONE.getDefaultState();
        BlockState lantern = Blocks.SEA_LANTERN.getDefaultState();
        BlockState glass = Blocks.GLASS.getDefaultState();

        // clear the volume (and a little around it)
        for (int x = X0 - 1; x <= X1 + 1; x++) {
            for (int z = Z0 - 1; z <= Z1 + 1; z++) {
                for (int y = FLOOR - 1; y <= TOP + 4; y++) set(w, x, y, z, air);
            }
        }
        // foundation columns so the room never floats over a valley
        for (int x = X0; x <= X1; x++) {
            for (int z = Z0; z <= Z1; z++) {
                for (int y = FLOOR - 1; y >= FLOOR - 40 && y > w.getBottomY(); y--) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (w.getBlockState(p).isOpaqueFullCube(w, p)) break;
                    set(w, x, y, z, packed);
                }
            }
        }
        for (int x = X0; x <= X1; x++) {
            for (int z = Z0; z <= Z1; z++) {
                boolean edgeX = x == X0 || x == X1, edgeZ = z == Z0 || z == Z1;
                boolean edge = edgeX || edgeZ;
                // floor: checkerboard of packed and blue ice
                set(w, x, FLOOR, z, edge || ((x + z) & 1) == 0 ? packed : blue);
                // ceiling
                set(w, x, TOP, z, edge ? blue : packed);
                // walls
                if (edge) {
                    for (int y = FLOOR + 1; y < TOP; y++) {
                        BlockState s = (edgeX && edgeZ) ? blue : (((edgeX ? z : x) - X0) % 4 == 0 ? blue : packed);
                        set(w, x, y, z, s);
                    }
                }
            }
        }
        // glowstone lighting: ceiling grid and wall lamps
        for (int i = 3; i <= 11; i += 4) {
            for (int j = 3; j <= 11; j += 4) set(w, X0 + i, TOP, Z0 + j, glow);
            set(w, X0 + i, FLOOR + 4, Z0, glow);
            set(w, X0 + i, FLOOR + 4, Z1, glow);
            set(w, X0, FLOOR + 4, Z0 + i, glow);
            set(w, X1, FLOOR + 4, Z0 + i, glow);
        }
        // glass display pedestals: a 3 x 3 glass plate in the floor, lit from below by glowstone, carrying the showcase
        for (int i = 0; i < SHOWCASES.length; i++) {
            BlockPos s = SHOWCASES[i];
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    set(w, s.getX() + dx, FLOOR, s.getZ() + dz, glass);
                    set(w, s.getX() + dx, FLOOR - 1, s.getZ() + dz, (dx == 0 && dz == 0) ? glow : lantern);
                }
            }
            w.setBlockState(s, ModBlocks.SHOWCASE.getDefaultState().with(ShowcaseBlock.TIER, i + 1), FLAGS);
        }
        // exit portal: a glowing arch against the north wall, facing into the room (with a sea lantern carpet)
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) set(w, CX + dx, FLOOR, Z0 + 2 + dz, lantern);
        }
        BlockState exit = ModBlocks.EXIT_PORTAL.getDefaultState().with(com.iceage.yantra.block.PortalBlock.FACING, net.minecraft.util.math.Direction.SOUTH)
                .with(com.iceage.yantra.block.PortalBlock.HALF, net.minecraft.block.enums.DoubleBlockHalf.LOWER);
        w.setBlockState(new BlockPos(CX, FLOOR + 1, Z0 + 2), exit, FLAGS);
        w.setBlockState(new BlockPos(CX, FLOOR + 2, Z0 + 2), exit.with(com.iceage.yantra.block.PortalBlock.HALF, net.minecraft.block.enums.DoubleBlockHalf.UPPER), FLAGS);
    }
}
'''),
    'src/main/java/com/iceage/yantra/ShopPortals.java': ('text', r'''package com.iceage.yantra;

import net.minecraft.block.BlockState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Teleport logic of the Village Portal and the Exit Portal. Same dimension, so no loading screens. */
public final class ShopPortals {
    private static final Map<UUID, Integer> COOLDOWN = new HashMap<>();

    private ShopPortals() {
    }

    private static boolean cooling(ServerPlayerEntity p) {
        int now = p.server.getTicks();
        Integer until = COOLDOWN.get(p.getUuid());
        if (until != null && now < until) return true;
        COOLDOWN.put(p.getUuid(), now + 40);
        return false;
    }

    /** Walking into a Village Portal: remember where we came from and go to the shop floor. */
    public static void enterShop(ServerPlayerEntity p, BlockPos portalBase, Direction facing) {
        MinecraftServer server = p.server;
        ServerWorld overworld = server.getOverworld();
        if (p.getServerWorld() != overworld) return;
        if (cooling(p)) return;

        // return position: two blocks in front of the portal, on the side we came from
        Vec3d c = Vec3d.ofBottomCenter(portalBase);
        double side = (p.getX() - c.x) * facing.getOffsetX() + (p.getZ() - c.z) * facing.getOffsetZ();
        int s = side >= 0 ? 1 : -1;
        double dx = facing.getOffsetX() * s, dz = facing.getOffsetZ() * s;
        IceData data = IceData.get(server);
        IceData.Entry e = data.entry(p.getUuid());
        e.hasReturn = true;
        e.rx = c.x + dx * 2.0;
        e.ry = portalBase.getY();
        e.rz = c.z + dz * 2.0;
        e.ryaw = (float) (MathHelper.atan2(-dx, dz) * 57.29577951308232);
        data.markDirty();

        ShopBuilder.ensureBuilt(server);
        teleport(p, overworld, ShopBuilder.ARRIVAL, 0f, 5f);
        overworld.playSound(null, BlockPos.ofFloored(ShopBuilder.ARRIVAL), ModSounds.PORTAL_WHOOSH, SoundCategory.PLAYERS, 1.0f, 1.0f);
    }

    /** Stepping on the Exit Portal: back to IcePortalReturnPos, on solid ground. */
    public static void exitShop(ServerPlayerEntity p) {
        MinecraftServer server = p.server;
        ServerWorld overworld = server.getOverworld();
        if (cooling(p)) return;
        IceData.Entry e = IceData.get(server).entry(p.getUuid());
        Vec3d target;
        float yaw;
        if (e.hasReturn) {
            target = groundAt(overworld, new Vec3d(e.rx, e.ry, e.rz));
            yaw = e.ryaw;
        } else {
            BlockPos spawn = overworld.getSpawnPos();
            target = groundAt(overworld, new Vec3d(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5));
            yaw = 0f;
        }
        teleport(p, overworld, target, yaw, 0f);
        overworld.playSound(null, BlockPos.ofFloored(target), ModSounds.PORTAL_WHOOSH, SoundCategory.PLAYERS, 1.0f, 0.8f);
    }

    /** Teleports with zero velocity and zero fall distance. */
    public static void teleport(ServerPlayerEntity p, ServerWorld world, Vec3d pos, float yaw, float pitch) {
        p.stopRiding();
        p.fallDistance = 0f;
        p.teleport(world, pos.x, pos.y, pos.z, yaw, pitch);
        p.setVelocity(Vec3d.ZERO);
        p.fallDistance = 0f;
        p.velocityModified = true;
    }

    private static boolean passable(ServerWorld w, BlockPos p) {
        BlockState s = w.getBlockState(p);
        return s.getCollisionShape(w, p).isEmpty() && s.getFluidState().isEmpty();
    }

    /** Finds solid ground with two free blocks above it, near the given position (never mid-air, never inside blocks). */
    public static Vec3d groundAt(ServerWorld w, Vec3d near) {
        int x = MathHelper.floor(near.x), z = MathHelper.floor(near.z);
        w.getChunk(x >> 4, z >> 4);
        int y0 = MathHelper.floor(near.y);
        // scan down from just above the remembered feet position
        for (int y = Math.min(w.getTopY() - 3, y0 + 1); y > Math.max(w.getBottomY() + 1, y0 - 40); y--) {
            Vec3d r = check(w, x, y, z);
            if (r != null) return r;
        }
        // we were inside something: scan upwards instead
        for (int y = y0 + 2; y < Math.min(w.getTopY() - 3, y0 + 24); y++) {
            Vec3d r = check(w, x, y, z);
            if (r != null) return r;
        }
        BlockPos top = new BlockPos(x, w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z), z);
        return Vec3d.ofBottomCenter(top);
    }

    private static Vec3d check(ServerWorld w, int x, int y, int z) {
        BlockPos g = new BlockPos(x, y, z);
        BlockState gs = w.getBlockState(g);
        if (gs.getCollisionShape(w, g).isEmpty()) return null;
        if (!passable(w, g.up()) || !passable(w, g.up(2))) return null;
        double top = g.getY() + gs.getCollisionShape(w, g).getMax(Direction.Axis.Y);
        return new Vec3d(x + 0.5, top, z + 0.5);
    }
}
'''),
    'src/main/java/com/iceage/yantra/Tier.java': ('text', r'''package com.iceage.yantra;

/** The five tiers of the shop. Price = frozen blocks needed to unlock the item at the showcase. */
public enum Tier {
    GOLA(1, "gola_machine", 0L, "Desi Baraf Gola Machine", 0x7FE6FF),
    FAN(2, "farrata_fan", 25L, "Farrata Table Fan", 0x8FD8FF),
    FRIDGE(3, "retro_fridge", 150L, "Retro Single-Door Fridge", 0xA8F0D8),
    AC(4, "ac_unit", 1000L, "Split AC Unit", 0xC0E0FF),
    VAN(5, "ice_cream_van", 50000L, "Musical Ice Cream Van", 0xFFB0E0);

    public static final long FINAL_TARGET = 10_000_000L;

    public final int number;
    public final String id;
    public final long price;
    public final String displayName;
    public final int color;

    Tier(int number, String id, long price, String displayName, int color) {
        this.number = number;
        this.id = id;
        this.price = price;
        this.displayName = displayName;
        this.color = color;
    }

    public static Tier byNumber(int n) {
        for (Tier t : values()) {
            if (t.number == n) return t;
        }
        return GOLA;
    }
}
'''),
    'src/main/java/com/iceage/yantra/YantraActions.java': ('text', r'''package com.iceage.yantra;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;

/** What a Yantra does when activated: 20-30 flying ice blocks, an instant freeze of the ground, points. */
public final class YantraActions {
    private static final Map<Long, Long> COOLDOWN = new HashMap<>();

    private YantraActions() {
    }

    public static void activate(ServerWorld world, BlockPos pos, Direction facing, ServerPlayerEntity player, Tier tier) {
        long now = world.getTime();
        Long last = COOLDOWN.get(pos.asLong());
        if (last != null && now - last < 25) return;
        COOLDOWN.put(pos.asLong(), now);
        if (COOLDOWN.size() > 512) COOLDOWN.clear();

        world.addSyncedBlockEvent(pos, world.getBlockState(pos).getBlock(), com.iceage.yantra.block.YantraBlockEntity.EVENT_ACTIVATE, 0);
        int projectiles = 20 + world.random.nextInt(11);
        launch(world, pos, facing, tier, projectiles);
        int frozen = freeze(world, pos, facing, tier);
        effects(world, pos, facing, tier);

        long mult = switch (tier) {
            case GOLA -> 1;
            case FAN -> 3;
            case FRIDGE -> 8;
            case AC -> 25;
            case VAN -> 1;
        };
        IcePoints.award(world.getServer(), player.getUuid(), frozen + projectiles * mult);
    }

    /** Fires the flying ice blocks (real falling-block entities) that land and become packed/blue ice. */
    private static void launch(ServerWorld world, BlockPos pos, Direction facing, Tier tier, int count) {
        BlockPos spawn = pos.up();
        if (!world.getBlockState(spawn).isAir()) spawn = pos.up(2);
        if (!world.getBlockState(spawn).isAir()) return;
        double fx = facing.getOffsetX(), fz = facing.getOffsetZ();
        double baseAngle = Math.atan2(fz, fx);
        for (int i = 0; i < count; i++) {
            double angle;
            double speed;
            double vy;
            switch (tier) {
                case GOLA -> {
                    angle = world.random.nextDouble() * Math.PI * 2;
                    speed = 0.7 + world.random.nextDouble() * 0.5;
                    vy = 0.45 + world.random.nextDouble() * 0.35;
                }
                case FAN -> {
                    angle = baseAngle + (world.random.nextDouble() - 0.5) * 1.2;
                    speed = 0.9 + world.random.nextDouble() * 0.5;
                    vy = 0.25 + world.random.nextDouble() * 0.3;
                }
                case FRIDGE -> {
                    angle = baseAngle + (world.random.nextDouble() - 0.5) * 2.0;
                    speed = 0.6 + world.random.nextDouble() * 0.4;
                    vy = 0.6 + world.random.nextDouble() * 0.3;
                }
                default -> {
                    angle = baseAngle + (world.random.nextDouble() - 0.5) * 2.6;
                    speed = 1.0 + world.random.nextDouble() * 0.5;
                    vy = 0.2 + world.random.nextDouble() * 0.3;
                }
            }
            BlockState ice = world.random.nextInt(3) == 0 ? Blocks.BLUE_ICE.getDefaultState() : Blocks.PACKED_ICE.getDefaultState();
            FallingBlockEntity fb = FallingBlockEntity.spawnFromBlock(world, spawn, ice);
            fb.dropItem = false;
            fb.setHurtEntities(2.0f, 20);
            fb.setPosition(pos.getX() + 0.5 + Math.cos(angle) * 0.7, spawn.getY() + 0.2, pos.getZ() + 0.5 + Math.sin(angle) * 0.7);
            fb.setVelocity(Math.cos(angle) * speed, vy, Math.sin(angle) * speed);
            fb.velocityModified = true;
        }
    }

    /** Instantly freezes the ground around the Yantra into packed ice. Returns the number of changed blocks. */
    private static int freeze(ServerWorld world, BlockPos pos, Direction facing, Tier tier) {
        int changed = 0;
        int y = pos.getY();
        switch (tier) {
            case GOLA -> changed += circle(world, pos, 4, y);
            case FRIDGE -> changed += circle(world, pos, 6, y);
            case AC -> changed += circle(world, pos, 9, y);
            case FAN -> {
                Direction side = facing.rotateYClockwise();
                for (int d = 0; d <= 11; d++) {
                    int half = Math.max(1, (int) Math.round(d * 0.55));
                    for (int l = -half; l <= half; l++) {
                        int x = pos.getX() + facing.getOffsetX() * d + side.getOffsetX() * l;
                        int z = pos.getZ() + facing.getOffsetZ() * d + side.getOffsetZ() * l;
                        changed += FreezeEngine.freezeColumn(world, x, z, y, 4, 6);
                    }
                }
            }
            default -> {
            }
        }
        return changed;
    }

    private static int circle(ServerWorld world, BlockPos c, int r, int y) {
        int changed = 0;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz > r * r + 1) continue;
                changed += FreezeEngine.freezeColumn(world, c.getX() + dx, c.getZ() + dz, y, 4, 6);
            }
        }
        return changed;
    }

    private static void effects(ServerWorld world, BlockPos pos, Direction facing, Tier tier) {
        Vec3d c = Vec3d.ofCenter(pos).add(0, 0.6, 0);
        world.spawnParticles(ModParticles.SNOWFLAKE, c.x, c.y, c.z, 60, 1.2, 0.8, 1.2, 0.15);
        world.spawnParticles(ModParticles.ICE_SHARD, c.x, c.y, c.z, 40, 0.8, 0.6, 0.8, 0.35);
        world.spawnParticles(ModParticles.FROST_MIST, c.x, c.y, c.z, 14, 0.8, 0.5, 0.8, 0.05);
        world.playSound(null, pos, ModSounds.FREEZE, SoundCategory.BLOCKS, 1.4f, 0.8f + 0.1f * tier.number);
        world.playSound(null, pos, tier == Tier.GOLA ? ModSounds.CRANK : tier == Tier.FAN ? ModSounds.FAN_WHOOSH : tier == Tier.FRIDGE ? ModSounds.FRIDGE_OPEN : ModSounds.AC_BLIZZARD, SoundCategory.BLOCKS, 1.2f, 1.0f);
        world.playSound(null, pos, SoundEvents.BLOCK_POWDER_SNOW_BREAK, SoundCategory.BLOCKS, 1.0f, 0.8f);
        int ringPoints = 36;
        double r = 0.5 + tier.number;
        for (int i = 0; i < ringPoints; i++) {
            double a = i * Math.PI * 2 / ringPoints;
            world.spawnParticles(ModParticles.SPARKLE, c.x + Math.cos(a) * 0.4, c.y, c.z + Math.sin(a) * 0.4, 0,
                    Math.cos(a), 0.05, Math.sin(a), 0.2 * MathHelper.clamp(r, 1, 4));
        }
    }
}
'''),
    'src/main/java/com/iceage/yantra/block/ExitPortalBlock.java': ('text', r'''package com.iceage.yantra.block;

import com.iceage.yantra.ShopPortals;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/** The Exit Portal inside the Ice Shop: step into it to return to the village. */
public class ExitPortalBlock extends PortalBlock {
    public ExitPortalBlock(Settings settings) {
        super(settings);
    }

    @Override
    protected void onPlayerEntered(ServerPlayerEntity player, BlockPos base, Direction facing) {
        ShopPortals.exitShop(player);
    }
}
'''),
    'src/main/java/com/iceage/yantra/block/PortalBlock.java': ('text', r'''package com.iceage.yantra.block;

import com.iceage.yantra.ShopPortals;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import com.iceage.yantra.ModBlocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.World;

/** The Village Portal: a two-block-tall arch. Walking into it sends you to the Ice Shop (no dimension change). */
public class PortalBlock extends HorizontalFacingBlock implements BlockEntityProvider {
    public static final EnumProperty<DoubleBlockHalf> HALF = Properties.DOUBLE_BLOCK_HALF;

    public PortalBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH).with(HALF, DoubleBlockHalf.LOWER));
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return state.get(HALF) == DoubleBlockHalf.LOWER ? BlockRenderType.ENTITYBLOCK_ANIMATED : BlockRenderType.INVISIBLE;
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return state.get(HALF) == DoubleBlockHalf.LOWER ? new PortalBlockEntity(pos, state) : null;
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        if (!world.isClient || type != ModBlocks.PORTAL_BE) return null;
        return (w, p, s, be) -> PortalBlockEntity.clientTick(w, p, s, (PortalBlockEntity) be);
    }

    /** What happens when a player walks into the arch. Overridden by the exit portal. */
    protected void onPlayerEntered(ServerPlayerEntity player, BlockPos base, Direction facing) {
        ShopPortals.enterShop(player, base, facing);
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        BlockPos pos = ctx.getBlockPos();
        World world = ctx.getWorld();
        if (pos.getY() < world.getTopY() - 1 && world.getBlockState(pos.up()).canReplace(ctx)) {
            return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite()).with(HALF, DoubleBlockHalf.LOWER);
        }
        return null;
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        world.setBlockState(pos.up(), state.with(HALF, DoubleBlockHalf.UPPER), Block.NOTIFY_ALL);
    }

    @Override
    public BlockState getStateForNeighborUpdate(BlockState state, Direction dir, BlockState neighbor, WorldAccess world, BlockPos pos, BlockPos neighborPos) {
        DoubleBlockHalf half = state.get(HALF);
        if (dir.getAxis() == Direction.Axis.Y && (half == DoubleBlockHalf.LOWER) == (dir == Direction.UP)) {
            if (neighbor.isOf(this) && neighbor.get(HALF) != half) return state;
            return net.minecraft.block.Blocks.AIR.getDefaultState();
        }
        if (half == DoubleBlockHalf.LOWER && dir == Direction.DOWN && !state.canPlaceAt(world, pos)) {
            return net.minecraft.block.Blocks.AIR.getDefaultState();
        }
        return super.getStateForNeighborUpdate(state, dir, neighbor, world, pos, neighborPos);
    }

    @Override
    public void onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        BlockPos other = state.get(HALF) == DoubleBlockHalf.LOWER ? pos.up() : pos.down();
        BlockState os = world.getBlockState(other);
        if (os.isOf(this) && os.get(HALF) != state.get(HALF)) {
            world.setBlockState(other, net.minecraft.block.Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL | Block.SKIP_DROPS);
        }
        super.onBreak(world, pos, state, player);
    }

    @Override
    public boolean canPlaceAt(BlockState state, net.minecraft.world.WorldView world, BlockPos pos) {
        BlockPos below = pos.down();
        BlockState bs = world.getBlockState(below);
        return state.get(HALF) == DoubleBlockHalf.LOWER ? bs.isSideSolidFullSquare(world, below, Direction.UP) : bs.isOf(this);
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext ctx) {
        return VoxelShapes.fullCube();
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext ctx) {
        return VoxelShapes.empty();
    }

    @Override
    public void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity) {
        if (!world.isClient && entity instanceof ServerPlayerEntity sp && !sp.hasVehicle() && !sp.isSpectator()) {
            BlockPos base = state.get(HALF) == DoubleBlockHalf.LOWER ? pos : pos.down();
            onPlayerEntered(sp, base, state.get(FACING));
        }
    }

    @Override
    public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
        if (random.nextInt(3) == 0) {
            world.addParticle(ParticleTypes.SNOWFLAKE, pos.getX() + random.nextDouble(), pos.getY() + random.nextDouble(),
                    pos.getZ() + random.nextDouble(), 0, 0.02, 0);
        }
    }
}
'''),
    'src/main/java/com/iceage/yantra/block/PortalBlockEntity.java': ('text', r'''package com.iceage.yantra.block;

import com.iceage.yantra.ModBlocks;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Client animation clock of a portal arch (the swirling vortex). Lives in the lower half only. */
public class PortalBlockEntity extends BlockEntity {
    public int age;

    public PortalBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.PORTAL_BE, pos, state);
    }

    public static void clientTick(World world, BlockPos pos, BlockState state, PortalBlockEntity be) {
        be.age++;
    }
}
'''),
    'src/main/java/com/iceage/yantra/block/ShowcaseBlock.java': ('text', r'''package com.iceage.yantra.block;

import com.iceage.yantra.IcePoints;
import com.iceage.yantra.ModItems;
import com.iceage.yantra.Tier;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import com.iceage.yantra.ModBlocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.IntProperty;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

/** A display piece in the Ice Shop. Right-click to take the item once you froze enough blocks. */
public class ShowcaseBlock extends BlockWithEntity {
    public static final IntProperty TIER = IntProperty.of("tier", 1, 5);

    public ShowcaseBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(TIER, 1));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(TIER);
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        if (!world.isClient || type != ModBlocks.SHOWCASE_BE) return null;
        return (w, p, s, be) -> ShowcaseBlockEntity.clientTick(w, p, s, (ShowcaseBlockEntity) be);
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext ctx) {
        return VoxelShapes.fullCube();
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new ShowcaseBlockEntity(pos, state);
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (world.isClient) return ActionResult.SUCCESS;
        Tier tier = Tier.byNumber(state.get(TIER));
        long points = IcePoints.get(world.getServer(), player.getUuid());
        if (points < tier.price) {
            world.playSound(null, pos, SoundEvents.ENTITY_VILLAGER_NO, SoundCategory.BLOCKS, 0.8f, 1.2f);
            return ActionResult.CONSUME;
        }
        ItemStack stack = new ItemStack(ModItems.forTier(tier));
        if (!player.getInventory().insertStack(stack)) player.dropItem(stack, false);
        world.playSound(null, pos, com.iceage.yantra.ModSounds.PURCHASE, SoundCategory.BLOCKS, 1.0f, 1.0f);
        return ActionResult.CONSUME;
    }
}
'''),
    'src/main/java/com/iceage/yantra/block/ShowcaseBlockEntity.java': ('text', r'''package com.iceage.yantra.block;

import com.iceage.yantra.ModBlocks;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Client animation clock of a shop pedestal (floating, spinning Yantra above it). */
public class ShowcaseBlockEntity extends BlockEntity {
    public int age;

    public ShowcaseBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.SHOWCASE_BE, pos, state);
    }

    public static void clientTick(World world, BlockPos pos, BlockState state, ShowcaseBlockEntity be) {
        be.age++;
    }
}
'''),
    'src/main/java/com/iceage/yantra/block/YantraBlock.java': ('text', r'''package com.iceage.yantra.block;

import com.iceage.yantra.Tier;
import com.iceage.yantra.YantraActions;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.state.property.DirectionProperty;
import com.iceage.yantra.ModBlocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

/** A Yantra: right-click to fire 20-30 flying ice blocks and instantly freeze the ground around it. Reusable forever. */
public class YantraBlock extends BlockWithEntity {
    public static final DirectionProperty FACING = HorizontalFacingBlock.FACING;
    public final Tier tier;
    private final VoxelShape shape;

    public YantraBlock(Settings settings, Tier tier, VoxelShape shape) {
        super(settings);
        this.tier = tier;
        this.shape = shape;
        setDefaultState(getStateManager().getDefaultState().with(FACING, net.minecraft.util.math.Direction.NORTH));
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new YantraBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        if (!world.isClient || type != ModBlocks.YANTRA_BE) return null;
        return (w, p, s, be) -> YantraBlockEntity.clientTick(w, p, s, (YantraBlockEntity) be);
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
    }

    @Override
    public BlockState rotate(BlockState state, BlockRotation rotation) {
        return state.with(FACING, rotation.rotate(state.get(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, BlockMirror mirror) {
        return state.rotate(mirror.getRotation(state.get(FACING)));
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext ctx) {
        return shape;
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (world.isClient) return ActionResult.SUCCESS;
        if (world instanceof ServerWorld sw && player instanceof ServerPlayerEntity sp) {
            YantraActions.activate(sw, pos, state.get(FACING), sp, tier);
        }
        return ActionResult.CONSUME;
    }
}
'''),
    'src/main/java/com/iceage/yantra/block/YantraBlockEntity.java': ('text', r'''package com.iceage.yantra.block;

import com.iceage.yantra.ModBlocks;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.world.World;
import net.minecraft.util.math.BlockPos;

/**
 * Holds only the client animation state of a Yantra (wheel, blades, rotor, fridge door). The server just sends a
 * synced block event whenever somebody uses the machine.
 */
public class YantraBlockEntity extends BlockEntity {
    public static final int EVENT_ACTIVATE = 1;
    public static final int RUN_TICKS = 90;

    public int age;
    public int active;
    public float angle, prevAngle, speed, door, prevDoor;

    public YantraBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.YANTRA_BE, pos, state);
    }

    @Override
    public boolean onSyncedBlockEvent(int type, int data) {
        if (type == EVENT_ACTIVATE) {
            active = RUN_TICKS;
            return true;
        }
        return super.onSyncedBlockEvent(type, data);
    }

    public static void clientTick(World world, BlockPos pos, BlockState state, YantraBlockEntity be) {
        be.age++;
        be.prevAngle = be.angle;
        be.prevDoor = be.door;
        float target = be.active > 0 ? 1f : 0f;
        if (be.active > 0) be.active--;
        be.speed += (target * 52f - be.speed) * (target > 0 ? 0.12f : 0.04f);
        if (be.speed < 0.05f) be.speed = 0;
        be.angle += be.speed;
        // fridge: door swings open while running, then closes
        float doorTarget = be.active > 6 ? 108f : 0f;
        be.door += (doorTarget - be.door) * (doorTarget > be.door ? 0.16f : 0.3f);
    }
}
'''),
    'src/main/java/com/iceage/yantra/client/ClientIceState.java': ('text', r'''package com.iceage.yantra.client;

/** Client mirror of the counter plus the animation state of the HUD (rolling number, pop, one floating +N). */
public final class ClientIceState {
    public static long points;
    public static float speed = 1.0f;
    public static double displayed, prevDisplayed;
    public static int pop;
    /** The single floating "+N" number: its amount and age in ticks (age >= LIFE means hidden). */
    public static long floaterAmount;
    public static int floaterAge = 999;
    public static final int FLOATER_LIFE = 30;
    private static boolean initialised;

    private ClientIceState() {
    }

    public static void update(long newPoints, float newSpeed, long gained) {
        points = newPoints;
        speed = newSpeed;
        if (!initialised) {
            displayed = prevDisplayed = newPoints;
            initialised = true;
            return;
        }
        if (gained > 0) {
            pop = 8;
            // exactly one floating number at a time: young ones merge, otherwise it restarts
            if (floaterAge < 12) {
                floaterAmount += gained;
            } else {
                floaterAmount = gained;
                floaterAge = 0;
            }
        }
        if (newPoints < displayed) displayed = prevDisplayed = newPoints;
    }

    public static void tick() {
        prevDisplayed = displayed;
        double diff = points - displayed;
        if (Math.abs(diff) < 0.5) {
            displayed = points;
        } else {
            double step = diff * 0.18;
            if (Math.abs(step) < 0.35) step = Math.signum(diff) * 0.35;
            displayed += step;
        }
        if (pop > 0) pop--;
        if (floaterAge < 999) floaterAge++;
    }

    public static void reset() {
        initialised = false;
        points = 0;
        speed = 1.0f;
        displayed = prevDisplayed = 0;
        floaterAge = 999;
    }
}
'''),
    'src/main/java/com/iceage/yantra/client/FrostParticles.java': ('text', r'''package com.iceage.yantra.client;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.particle.SpriteBillboardParticle;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.DefaultParticleType;
import net.minecraft.util.math.MathHelper;

/** Frost particle family: vortex swirls, crushed-ice shards, snowflakes, freezing fog and sparkles (ported from Forge). */
public final class FrostParticles {
    private FrostParticles() {
    }

    public enum Kind { SWIRL, SHARD, SNOW, MIST, SPARKLE }

    public static class Frost extends SpriteBillboardParticle {
        private final SpriteProvider sprites;
        private final Kind kind;
        private final float spin;
        private final float baseSize;
        private final boolean glow;

        Frost(ClientWorld world, double x, double y, double z, double vx, double vy, double vz, SpriteProvider sprites, Kind kind) {
            super(world, x, y, z, vx, vy, vz);
            this.sprites = sprites;
            this.kind = kind;
            this.velocityX = vx;
            this.velocityY = vy;
            this.velocityZ = vz;
            this.spin = (random.nextFloat() - 0.5f) * 0.3f;
            switch (kind) {
                case SWIRL -> {
                    maxAge = 18 + random.nextInt(14);
                    scale = 0.12f + random.nextFloat() * 0.14f;
                    velocityMultiplier = 0.94f;
                    gravityStrength = 0f;
                    glow = true;
                    setSpriteForAge(sprites);
                }
                case SHARD -> {
                    maxAge = 18 + random.nextInt(22);
                    scale = 0.05f + random.nextFloat() * 0.07f;
                    velocityMultiplier = 0.96f;
                    gravityStrength = 0.9f;
                    glow = false;
                    setSprite(sprites);
                }
                case SNOW -> {
                    maxAge = 40 + random.nextInt(50);
                    scale = 0.045f + random.nextFloat() * 0.06f;
                    velocityMultiplier = 0.93f;
                    gravityStrength = 0.03f;
                    glow = false;
                    setSprite(sprites);
                }
                case MIST -> {
                    maxAge = 40 + random.nextInt(40);
                    scale = 0.45f + random.nextFloat() * 0.6f;
                    velocityMultiplier = 0.95f;
                    gravityStrength = 0.004f;
                    glow = false;
                    alpha = 0f;
                    setSprite(sprites);
                }
                default -> {
                    maxAge = 10 + random.nextInt(14);
                    scale = 0.06f + random.nextFloat() * 0.08f;
                    velocityMultiplier = 0.9f;
                    gravityStrength = 0f;
                    glow = true;
                    setSpriteForAge(sprites);
                }
            }
            baseSize = scale;
            collidesWithWorld = kind == Kind.SHARD || kind == Kind.SNOW;
            angle = random.nextFloat() * MathHelper.TAU;
            prevAngle = angle;
        }

        @Override
        public void tick() {
            super.tick();
            prevAngle = angle;
            float life = (float) age / maxAge;
            switch (kind) {
                case SWIRL, SPARKLE -> {
                    setSpriteForAge(sprites);
                    alpha = 1f - life * life;
                    angle += spin * 2f;
                }
                case SHARD -> {
                    angle += spin * 3f;
                    if (onGround) {
                        velocityX *= 0.6;
                        velocityZ *= 0.6;
                    }
                }
                case SNOW -> {
                    angle += spin;
                    velocityX += MathHelper.sin(age * 0.2f + baseSize * 100) * 0.0025;
                    velocityZ += MathHelper.cos(age * 0.17f + baseSize * 50) * 0.0025;
                    alpha = life > 0.8f ? (1f - life) * 5f : 1f;
                }
                case MIST -> {
                    angle += spin * 0.15f;
                    alpha = (life < 0.2f ? life / 0.2f : 1f - (life - 0.2f) / 0.8f) * 0.42f;
                    scale = baseSize * (1f + life * 0.9f);
                }
            }
        }

        @Override
        protected int getBrightness(float tint) {
            if (glow) return 0xF000F0;
            int base = super.getBrightness(tint);
            int bl = Math.max(base & 0xFF, 0x80);
            return (base & 0xFF0000) | bl;
        }

        @Override
        public ParticleTextureSheet getType() {
            return ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT;
        }
    }

    public static class Provider implements ParticleFactory<DefaultParticleType> {
        private final SpriteProvider sprites;
        private final Kind kind;

        public Provider(SpriteProvider sprites, Kind kind) {
            this.sprites = sprites;
            this.kind = kind;
        }

        @Override
        public Particle createParticle(DefaultParticleType type, ClientWorld world, double x, double y, double z, double vx, double vy, double vz) {
            return new Frost(world, x, y, z, vx, vy, vz, sprites, kind);
        }
    }
}
'''),
    'src/main/java/com/iceage/yantra/client/IceAgeYantraClient.java': ('text', r'''package com.iceage.yantra.client;

import com.iceage.yantra.ModBlocks;
import com.iceage.yantra.ModEntities;
import com.iceage.yantra.Net;
import com.iceage.yantra.client.obj.ObjParts;
import com.iceage.yantra.client.render.GotiyaRenderer;
import com.iceage.yantra.client.render.ItemRigs;
import com.iceage.yantra.client.render.PortalRenderer;
import com.iceage.yantra.client.render.ShowcaseRenderer;
import com.iceage.yantra.client.render.VanRenderer;
import com.iceage.yantra.client.render.YantraRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import com.iceage.yantra.ModParticles;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;

/** Client side: HUD counter, OBJ rig renderers, entity renderers. Pure Fabric API, no mixins. */
public class IceAgeYantraClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        BlockEntityRendererFactories.register(ModBlocks.YANTRA_BE, YantraRenderer::new);
        BlockEntityRendererFactories.register(ModBlocks.PORTAL_BE, PortalRenderer::new);
        BlockEntityRendererFactories.register(ModBlocks.SHOWCASE_BE, ShowcaseRenderer::new);
        EntityRendererRegistry.register(ModEntities.ICE_VAN, VanRenderer::new);
        EntityRendererRegistry.register(ModEntities.GOTIYA, GotiyaRenderer::new);
        ItemRigs.register();
        ParticleFactoryRegistry pf = ParticleFactoryRegistry.getInstance();
        pf.register(ModParticles.FROST_SWIRL, s -> new FrostParticles.Provider(s, FrostParticles.Kind.SWIRL));
        pf.register(ModParticles.ICE_SHARD, s -> new FrostParticles.Provider(s, FrostParticles.Kind.SHARD));
        pf.register(ModParticles.SNOWFLAKE, s -> new FrostParticles.Provider(s, FrostParticles.Kind.SNOW));
        pf.register(ModParticles.FROST_MIST, s -> new FrostParticles.Provider(s, FrostParticles.Kind.MIST));
        pf.register(ModParticles.SPARKLE, s -> new FrostParticles.Provider(s, FrostParticles.Kind.SPARKLE));
        com.iceage.yantra.entity.IceVanEntity.jingleHook = VanJingle::ensure;

        // re-parse the OBJ rigs whenever resources reload
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override
            public Identifier getFabricId() {
                return new Identifier("ice_age_yantra", "obj_rigs");
            }

            @Override
            public void reload(ResourceManager manager) {
                ObjParts.clearAll();
            }
        });

        HudRenderCallback.EVENT.register(IceHud::render);
        ClientTickEvents.END_CLIENT_TICK.register(c -> ClientIceState.tick());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientIceState.reset());
        ClientPlayNetworking.registerGlobalReceiver(Net.POINTS, (client, handler, buf, sender) -> {
            long points = buf.readLong();
            float speed = buf.readFloat();
            long gained = buf.readLong();
            client.execute(() -> ClientIceState.update(points, speed, gained));
        });
    }
}
'''),
    'src/main/java/com/iceage/yantra/client/IceHud.java': ('text', r'''package com.iceage.yantra.client;

import com.iceage.yantra.IceAgeYantra;
import com.iceage.yantra.entity.IceVanEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import com.mojang.blaze3d.systems.RenderSystem;

import java.util.Locale;

/**
 * Top-left frozen-blocks counter (ported unchanged from the Forge version): dark bevelled bar, ice cube icon, rolling
 * odometer number (" / 10,000,000") and the live multiplier on the right (counter speed x van combo).
 */
public final class IceHud {
    private static final Identifier BAR = new Identifier(IceAgeYantra.MODID, "textures/gui/counter_bar.png");
    private static final Identifier ICE_CUBE = new Identifier(IceAgeYantra.MODID, "textures/gui/ice_cube.png");

    private IceHud() {
    }

    private static void blitFull(DrawContext g, Identifier tex, int x, int y, int w, int h, int texW, int texH) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.drawTexture(tex, x, y, w, h, 0, 0, texW, texH, texW, texH);
    }

    public static void render(DrawContext g, float pt) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options.hudHidden || mc.player == null) return;
        TextRenderer font = mc.textRenderer;
        double shown = MathHelper.lerp(pt, ClientIceState.prevDisplayed, ClientIceState.displayed);

        float mult = ClientIceState.speed;
        if (mc.player.getVehicle() instanceof IceVanEntity van) mult *= van.combo();
        String multText = String.format(Locale.US, "x%.1f", mult);
        String total = " / 10,000,000";

        int numW = font.getWidth(String.format(Locale.US, "%,d", (long) Math.floor(shown)));
        int barW = Math.max(200, 30 + numW + font.getWidth(total) + 16 + font.getWidth(multText) + 12);
        int barH = 24;
        int x = 4;
        int y = 4;

        float pop = Math.max(0f, (ClientIceState.pop - pt) / 8f);
        float s = 1f + 0.05f * pop;
        g.getMatrices().push();
        g.getMatrices().translate(x + barW / 2f, y + barH / 2f, 0);
        g.getMatrices().scale(s, s, 1f);
        g.getMatrices().translate(-(x + barW / 2f), -(y + barH / 2f), 0);
        blitFull(g, BAR, x, y, barW, barH, 600, 96);
        g.getMatrices().pop();

        // ice cube icon
        float t = mc.player.age + pt;
        g.getMatrices().push();
        g.getMatrices().translate(x + 15, y + barH / 2f, 0);
        float bob = 1f + 0.05f * MathHelper.sin(t * 0.12f) + 0.15f * pop;
        g.getMatrices().scale(bob, bob, 1f);
        blitFull(g, ICE_CUBE, -9, -9, 18, 18, 128, 128);
        g.getMatrices().pop();

        int ty = y + (barH - 8) / 2;
        int nx = x + 30;
        int numColor = pop > 0 ? 0xFFBFF6FF : 0xFFFFFFFF;
        int w = rolling(g, font, shown, nx, ty, numColor);
        g.drawText(font, total, nx + w, ty, 0xFF7F8890, true);

        int multColor = mult > 1.0f ? 0xFF7FE6FF : 0xFFFFFFFF;
        g.drawText(font, multText, x + barW - 10 - font.getWidth(multText), ty, multColor, true);

        // exactly one floating number: drifts up and fades
        if (ClientIceState.floaterAge < ClientIceState.FLOATER_LIFE) {
            float life = (ClientIceState.floaterAge + pt) / ClientIceState.FLOATER_LIFE;
            int alpha = (int) (MathHelper.clamp(1f - life * life, 0f, 1f) * 255f);
            if (alpha >= 8) {
                String txt = "+" + String.format(Locale.US, "%,d", ClientIceState.floaterAmount);
                float fx = nx + font.getWidth(txt) * 0.4f;
                float fy = y + barH + 14 - life * 12f;
                g.getMatrices().push();
                g.getMatrices().translate(fx, fy, 0);
                g.getMatrices().scale(0.8f, 0.8f, 1f);
                g.drawText(font, txt, -font.getWidth(txt) / 2, 0, (alpha << 24) | 0x9FFFD8, true);
                g.getMatrices().pop();
            }
        }
    }

    /** Odometer-style rolling number: every digit scrolls into the next one. */
    private static int rolling(DrawContext g, TextRenderer font, double value, int x, int y, int color) {
        long whole = (long) Math.floor(Math.max(0, value));
        double frac = Math.max(0, value) - whole;
        String s = String.format(Locale.US, "%,d", whole);
        int digits = 0;
        for (char c : s.toCharArray()) if (Character.isDigit(c)) digits++;
        int lh = 10;
        int w = font.getWidth(s);
        g.enableScissor(x - 1, y - 1, x + w + 8, y + 9);
        int cx = x;
        int seen = 0;
        for (char c : s.toCharArray()) {
            if (!Character.isDigit(c)) {
                g.drawText(font, String.valueOf(c), cx, y, color, true);
                cx += font.getWidth(String.valueOf(c));
                continue;
            }
            int place = digits - 1 - seen++;
            long p10 = 1;
            for (int i = 0; i < place; i++) p10 *= 10;
            double roll = (whole % p10) == p10 - 1 ? frac : 0;
            int off = (int) Math.round(roll * lh);
            int d = c - '0';
            g.drawText(font, String.valueOf(d), cx, y - off, color, true);
            if (off > 0) g.drawText(font, String.valueOf((d + 1) % 10), cx, y - off + lh, color, true);
            cx += font.getWidth(String.valueOf(d));
        }
        g.disableScissor();
        return w;
    }
}
'''),
    'src/main/java/com/iceage/yantra/client/VanJingle.java': ('text', r'''package com.iceage.yantra.client;

import com.iceage.yantra.ModSounds;
import com.iceage.yantra.entity.IceVanEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundCategory;

import java.util.Map;
import java.util.WeakHashMap;

/** The van's musical jingle: a looping sound that follows the van while somebody is in it or it is rolling. */
public final class VanJingle extends MovingSoundInstance {
    private static final Map<IceVanEntity, VanJingle> ACTIVE = new WeakHashMap<>();
    private final IceVanEntity van;

    private VanJingle(IceVanEntity van) {
        super(ModSounds.VAN_JINGLE, SoundCategory.NEUTRAL, SoundInstance.createRandom());
        this.van = van;
        this.repeat = true;
        this.repeatDelay = 0;
        this.volume = 1.4f;
        this.attenuationType = SoundInstance.AttenuationType.LINEAR;
    }

    public static void ensure(IceVanEntity van) {
        VanJingle j = ACTIVE.get(van);
        if (j != null && !j.isDone()) return;
        j = new VanJingle(van);
        ACTIVE.put(van, j);
        MinecraftClient.getInstance().getSoundManager().play(j);
    }

    @Override
    public void tick() {
        if (van.isRemoved() || (van.getPassengerList().isEmpty() && Math.abs(van.groundSpeed) < 0.02f && van.age % 200 > 120)) {
            setDone();
            return;
        }
        this.x = van.getX();
        this.y = van.getY();
        this.z = van.getZ();
    }
}
'''),
    'src/main/java/com/iceage/yantra/client/obj/ObjModel.java': ('text', r'''package com.iceage.yantra.client.obj;

import com.iceage.yantra.IceAgeYantra;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * A baked OBJ rig part (the very same .obj/.mtl files the Forge version of the mod uses): triangles with per-vertex
 * normals, UVs and baked ambient-occlusion vertex colours, grouped by material. Materials with Ka 1 are emissive.
 * Rendered straight into the vertex consumer, so there are no mixins and no model-loader hooks.
 */
public final class ObjModel {
    /** Material names whose texture has a different file name. */
    private static final Map<String, String> ALIAS = Map.of(
            "chandelier_ice", "portal_icicle", "cone_stand", "gola_chrome", "fan_badge_ring", "gola_chrome", "fan_chrome", "gola_chrome");

    private static final class Group {
        Identifier texture;
        boolean emissive;
        float[] pos, uv, nrm, col;
        int tris;
        Sprite sprite;
    }

    private final List<Group> groups = new ArrayList<>();

    private ObjModel() {
    }

    // ------------------------------------------------------------------------------------------------ loading
    public static ObjModel load(Identifier objPath) {
        ObjModel m = new ObjModel();
        try {
            ResourceManagerHolder.parse(m, objPath);
        } catch (Exception e) {
            IceAgeYantra.LOGGER.error("Could not load OBJ model {}", objPath, e);
        }
        return m;
    }

    private static final class ResourceManagerHolder {
        static void parse(ObjModel m, Identifier objPath) throws IOException {
            var rm = MinecraftClient.getInstance().getResourceManager();
            Optional<Resource> res = rm.getResource(objPath);
            if (res.isEmpty()) throw new IOException("missing " + objPath);
            // materials: emissive flag from the .mtl next to the .obj
            Map<String, Boolean> emissive = new HashMap<>();
            Identifier mtlPath = new Identifier(objPath.getNamespace(), objPath.getPath().replaceAll("\\.obj$", ".mtl"));
            Optional<Resource> mtl = rm.getResource(mtlPath);
            if (mtl.isPresent()) {
                try (BufferedReader r = mtl.get().getReader()) {
                    String cur = null, line;
                    while ((line = r.readLine()) != null) {
                        line = line.trim();
                        if (line.startsWith("newmtl ")) cur = line.substring(7).trim();
                        else if (line.startsWith("Ka ") && cur != null) {
                            String[] p = line.split("\\s+");
                            emissive.put(cur, Float.parseFloat(p[1]) > 0.5f);
                        }
                    }
                }
            }
            List<float[]> v = new ArrayList<>(), vt = new ArrayList<>(), vn = new ArrayList<>(), vc = new ArrayList<>();
            Map<String, List<float[]>> perMat = new HashMap<>();   // 3 corners * (x y z u v nx ny nz r g b a)
            List<String> order = new ArrayList<>();
            String mat = "default";
            try (BufferedReader r = res.get().getReader()) {
                String line;
                while ((line = r.readLine()) != null) {
                    if (line.isEmpty() || line.charAt(0) == '#') continue;
                    String[] t = line.split("\\s+");
                    switch (t[0]) {
                        case "v" -> v.add(new float[]{f(t[1]), f(t[2]), f(t[3])});
                        case "vt" -> vt.add(new float[]{f(t[1]), f(t[2])});
                        case "vn" -> vn.add(new float[]{f(t[1]), f(t[2]), f(t[3])});
                        case "vc" -> vc.add(new float[]{f(t[1]), f(t[2]), f(t[3]), t.length > 4 ? f(t[4]) : 1f});
                        case "usemtl" -> mat = t[1];
                        case "o" -> {
                            if (!perMat.containsKey(t[1]) && !emissive.containsKey(t[1])) {
                                // object names equal the material names in these files; usemtl follows anyway
                            }
                        }
                        case "f" -> {
                            List<float[]> list = perMat.computeIfAbsent(mat, k -> {
                                order.add(k);
                                return new ArrayList<>();
                            });
                            int n = t.length - 1;
                            float[][] corner = new float[n][];
                            for (int i = 0; i < n; i++) {
                                String[] ix = t[i + 1].split("/");
                                float[] p = v.get(Integer.parseInt(ix[0]) - 1);
                                float[] uv = ix.length > 1 && !ix[1].isEmpty() ? vt.get(Integer.parseInt(ix[1]) - 1) : new float[]{0, 0};
                                float[] nn = ix.length > 2 && !ix[2].isEmpty() ? vn.get(Integer.parseInt(ix[2]) - 1) : new float[]{0, 1, 0};
                                float[] cc = ix.length > 3 && !ix[3].isEmpty() ? vc.get(Integer.parseInt(ix[3]) - 1) : new float[]{1, 1, 1, 1};
                                corner[i] = new float[]{p[0], p[1], p[2], uv[0], uv[1], nn[0], nn[1], nn[2], cc[0], cc[1], cc[2], cc[3]};
                            }
                            for (int i = 1; i < n - 1; i++) {   // triangle fan
                                list.add(corner[0]);
                                list.add(corner[i]);
                                list.add(corner[i + 1]);
                            }
                        }
                        default -> {
                        }
                    }
                }
            }
            for (String name : order) {
                List<float[]> c = perMat.get(name);
                Group g = new Group();
                String tex = ALIAS.getOrDefault(name, name);
                g.texture = new Identifier(IceAgeYantra.MODID, "block/" + tex);
                g.emissive = emissive.getOrDefault(name, false);
                g.tris = c.size() / 3;
                g.pos = new float[c.size() * 3];
                g.uv = new float[c.size() * 2];
                g.nrm = new float[c.size() * 3];
                g.col = new float[c.size() * 4];
                for (int i = 0; i < c.size(); i++) {
                    float[] k = c.get(i);
                    g.pos[i * 3] = k[0];
                    g.pos[i * 3 + 1] = k[1];
                    g.pos[i * 3 + 2] = k[2];
                    g.uv[i * 2] = k[3];
                    g.uv[i * 2 + 1] = k[4];
                    g.nrm[i * 3] = k[5];
                    g.nrm[i * 3 + 1] = k[6];
                    g.nrm[i * 3 + 2] = k[7];
                    g.col[i * 4] = k[8];
                    g.col[i * 4 + 1] = k[9];
                    g.col[i * 4 + 2] = k[10];
                    g.col[i * 4 + 3] = k[11];
                }
                m.groups.add(g);
            }
        }

        private static float f(String s) {
            return Float.parseFloat(s);
        }
    }

    // ------------------------------------------------------------------------------------------------ drawing
    public void render(MatrixStack ms, VertexConsumerProvider vcp, int light, int overlay) {
        render(ms, vcp, light, overlay, 1f, 1f, 1f, 1f);
    }

    public void render(MatrixStack ms, VertexConsumerProvider vcp, int light, int overlay, float r, float g, float b, float a) {
        if (groups.isEmpty()) return;
        Function<Identifier, Sprite> atlas = MinecraftClient.getInstance().getSpriteAtlas(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE);
        RenderLayer layer = a < 0.999f ? RenderLayer.getEntityTranslucent(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE)
                : RenderLayer.getEntityCutoutNoCull(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE);
        VertexConsumer vc = vcp.getBuffer(layer);
        MatrixStack.Entry e = ms.peek();
        Matrix4f pm = e.getPositionMatrix();
        Matrix3f nm = e.getNormalMatrix();
        for (Group gr : groups) {
            Sprite s = atlas.apply(gr.texture);
            float u0 = s.getMinU(), du = s.getMaxU() - u0, v0 = s.getMinV(), dv = s.getMaxV() - v0;
            int lt = gr.emissive ? LightmapTextureManager.MAX_LIGHT_COORDINATE : light;
            for (int t = 0; t < gr.tris; t++) {
                for (int k = 0; k < 4; k++) {          // triangles are drawn as degenerate quads
                    int i = t * 3 + Math.min(k, 2);
                    vc.vertex(pm, gr.pos[i * 3], gr.pos[i * 3 + 1], gr.pos[i * 3 + 2])
                            .color(gr.col[i * 4] * r, gr.col[i * 4 + 1] * g, gr.col[i * 4 + 2] * b, gr.col[i * 4 + 3] * a)
                            .texture(u0 + du * gr.uv[i * 2], v0 + dv * gr.uv[i * 2 + 1])
                            .overlay(overlay)
                            .light(lt)
                            .normal(nm, gr.nrm[i * 3], gr.nrm[i * 3 + 1], gr.nrm[i * 3 + 2])
                            .next();
                }
            }
        }
    }
}
'''),
    'src/main/java/com/iceage/yantra/client/obj/ObjParts.java': ('text', r'''package com.iceage.yantra.client.obj;

import com.iceage.yantra.IceAgeYantra;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/** The baked rig parts of the Forge version (gola, fan, fridge, AC, van, portal, pedestal, hairdryer). Lazily parsed. */
public final class ObjParts {
    private static final List<Part> ALL = new ArrayList<>();

    public static final class Part {
        private final Identifier path;
        private ObjModel model;

        private Part(String name) {
            this.path = new Identifier(IceAgeYantra.MODID, "models/obj/" + name + ".obj");
        }

        public void render(MatrixStack ms, VertexConsumerProvider vcp, int light, int overlay) {
            render(ms, vcp, light, overlay, 1f, 1f, 1f, 1f);
        }

        public void render(MatrixStack ms, VertexConsumerProvider vcp, int light, int overlay, float r, float g, float b, float a) {
            if (model == null) model = ObjModel.load(path);
            model.render(ms, vcp, light, overlay, r, g, b, a);
        }

        void clear() {
            model = null;
        }
    }

    private static Part part(String name) {
        Part p = new Part(name);
        ALL.add(p);
        return p;
    }

    public static final Part GOLA_BODY = part("gola/body"), GOLA_WHEEL = part("gola/wheel"), GOLA_HANDLE = part("gola/handle");
    public static final Part FAN_BODY = part("fan/body"), FAN_HEAD = part("fan/head"), FAN_ROTOR = part("fan/rotor");
    public static final Part FRIDGE_BODY = part("fridge/body"), FRIDGE_DOOR_TOP = part("fridge/door_top"), FRIDGE_DOOR_BOTTOM = part("fridge/door_bottom");
    public static final Part AC_BODY = part("ac/body"), AC_ROTOR = part("ac/rotor");
    public static final Part TRUCK_BODY = part("truck/body"), TRUCK_CONE = part("truck/cone"), TRUCK_DECALS = part("truck/decals"),
            TRUCK_GLASS = part("truck/glass"), TRUCK_WHEEL_FL = part("truck/wheel_fl"), TRUCK_WHEEL_FR = part("truck/wheel_fr"),
            TRUCK_WHEEL_RL = part("truck/wheel_rl"), TRUCK_WHEEL_RR = part("truck/wheel_rr");
    public static final Part PORTAL_FRAME = part("portal/frame"), PEDESTAL = part("pedestal/base"), HAIRDRYER = part("hairdryer/body");

    private ObjParts() {
    }

    /** Called on every resource reload so edited/new models and re-stitched atlases are picked up. */
    public static void clearAll() {
        for (Part p : ALL) p.clear();
    }
}
'''),
    'src/main/java/com/iceage/yantra/client/render/GotiyaRenderer.java': ('text', r'''package com.iceage.yantra.client.render;

import com.iceage.yantra.client.obj.ObjParts;
import com.iceage.yantra.entity.GotiyaEntity;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.feature.HeadFeatureRenderer;
import net.minecraft.client.render.entity.feature.VillagerClothingFeatureRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.VillagerResemblingModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

/** Gotiya: sweaty desert villager (vanilla texture, like the Forge version) with the giant baked-OBJ hairdryer. */
public class GotiyaRenderer extends MobEntityRenderer<GotiyaEntity, GotiyaRenderer.Model> {
    private static final Identifier TEXTURE = new Identifier("textures/entity/villager/villager.png");
    /** The hairdryer is drawn this many times bigger than its model (taller than Gotiya himself). */
    public static final float DRYER_SCALE = 4.0f;

    public GotiyaRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new Model(ctx.getPart(EntityModelLayers.VILLAGER)), 0.5f);
        addFeature(new HeadFeatureRenderer<>(this, ctx.getModelLoader(), ctx.getHeldItemRenderer()));
        addFeature(new VillagerClothingFeatureRenderer<>(this, ctx.getResourceManager(), "villager"));
    }

    @Override
    public Identifier getTexture(GotiyaEntity e) {
        return TEXTURE;
    }

    @Override
    protected void scale(GotiyaEntity e, MatrixStack ps, float amount) {
        ps.scale(0.9375f, 0.9375f, 0.9375f);
    }

    @Override
    public void render(GotiyaEntity e, float yaw, float pt, MatrixStack ps, VertexConsumerProvider buf, int light) {
        super.render(e, yaw, pt, ps, buf, light);
        if (!e.hasHairdryer()) return;
        float t = e.age + pt;
        float body = MathHelper.lerpAngleDegrees(pt, e.prevBodyYaw, e.bodyYaw);
        ps.push();
        ps.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-body));
        // hands up at chest height, the whole thing sways and dips as he struggles with the weight
        ps.translate(MathHelper.sin(t * 0.21f) * 0.08f, 1.35 + MathHelper.sin(t * 0.33f) * 0.06f, 0.45);
        ps.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(MathHelper.sin(t * 0.17f) * 9f + MathHelper.sin(t * 0.61f) * 2.5f));
        ps.multiply(RotationAxis.POSITIVE_X.rotationDegrees(14f + MathHelper.sin(t * 0.25f) * 6f));
        ps.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(MathHelper.sin(t * 0.09f) * 10f));
        ps.scale(DRYER_SCALE, DRYER_SCALE, DRYER_SCALE);
        ps.translate(0, 0.84, 0.19);   // grab it by the bottom of the handle
        ObjParts.HAIRDRYER.render(ps, buf, light, OverlayTexture.DEFAULT_UV);
        // glowing heat at the mouth of the nozzle
        YantraRig.glow(ps, buf, 0, 0, 0.74, 0.35f, 1.0f, 0.42f, 0.12f, 0.55f + 0.25f * MathHelper.sin(t * 0.9f));
        ps.pop();
    }

    @Override
    public boolean shouldRender(GotiyaEntity e, Frustum frustum, double x, double y, double z) {
        return e.hasHairdryer() || super.shouldRender(e, frustum, x, y, z);
    }

    /** Villager model with the arms posed for the struggle with the hairdryer / fleeing. */
    public static class Model extends VillagerResemblingModel<GotiyaEntity> {
        private final ModelPart arms, head;

        public Model(ModelPart root) {
            super(root);
            arms = root.getChild("arms");
            head = root.getChild("head");
        }

        @Override
        public void setAngles(GotiyaEntity e, float limbAngle, float limbDistance, float age, float headYaw, float headPitch) {
            super.setAngles(e, limbAngle, limbDistance, age, headYaw, headPitch);
            arms.pitch = -0.75f;
            arms.roll = 0f;
            if (e.hasHairdryer()) {
                arms.pitch = -1.9f + MathHelper.sin(age * 0.4f) * 0.12f;
                head.pitch = -0.35f;
                head.roll = MathHelper.sin(age * 0.3f) * 0.12f;
            } else if (e.state() == GotiyaEntity.FLEEING) {
                arms.pitch = -2.6f + MathHelper.sin(age * 0.9f) * 0.35f;
                arms.roll = MathHelper.sin(age * 1.3f) * 0.25f;
                head.roll = MathHelper.sin(age * 0.8f) * 0.2f;
            }
        }
    }
}
'''),
    'src/main/java/com/iceage/yantra/client/render/ItemRigs.java': ('text', r'''package com.iceage.yantra.client.render;

import com.iceage.yantra.ModItems;
import com.iceage.yantra.client.obj.ObjParts;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/** Item rendering: the full animated 3D rigs in the inventory, in hand and in item frames (ported from the Forge BEWLR). */
public final class ItemRigs {
    private ItemRigs() {
    }

    public static void register() {
        for (Item item : new Item[]{ModItems.GOLA_MACHINE, ModItems.FARRATA_FAN, ModItems.RETRO_FRIDGE, ModItems.AC_UNIT,
                ModItems.ICE_CREAM_VAN, ModItems.VILLAGE_PORTAL}) {
            BuiltinItemRendererRegistry.INSTANCE.register(item, ItemRigs::render);
        }
    }

    private static void render(ItemStack stack, ModelTransformationMode mode, MatrixStack ps, VertexConsumerProvider buf, int light, int overlay) {
        Item item = stack.getItem();
        MinecraftClient mc = MinecraftClient.getInstance();
        float time = (mc.world == null ? 0 : mc.world.getTime()) + mc.getTickDelta();
        ps.push();
        if (item == ModItems.GOLA_MACHINE) {
            YantraRig.gola(ps, buf, light, overlay, time * 3f, 0, 0);
        } else if (item == ModItems.FARRATA_FAN) {
            YantraRig.fan(ps, buf, light, overlay, time * 8f, 0, 0, time);
        } else if (item == ModItems.RETRO_FRIDGE) {
            ps.translate(0.5, 0, 0.5);
            ps.scale(0.5f, 0.5f, 0.5f);
            ps.translate(-0.5, 0, -0.5);
            YantraRig.fridge(ps, buf, light, overlay, 0, 0);
        } else if (item == ModItems.AC_UNIT) {
            YantraRig.ac(ps, buf, light, overlay, time * 6f);
        } else if (item == ModItems.ICE_CREAM_VAN) {
            ps.translate(0.5, 0.05, 0.5);
            ps.scale(0.2f, 0.2f, 0.2f);
            YantraRig.van(ps, buf, light, overlay, 0, 0, time, false);
        } else if (item == ModItems.VILLAGE_PORTAL) {
            ObjParts.PORTAL_FRAME.render(ps, buf, light, overlay);
        }
        ps.pop();
    }
}
'''),
    'src/main/java/com/iceage/yantra/client/render/PortalRenderer.java': ('text', r'''package com.iceage.yantra.client.render;

import com.iceage.yantra.IceAgeYantra;
import com.iceage.yantra.block.PortalBlock;
import com.iceage.yantra.block.PortalBlockEntity;
import com.iceage.yantra.client.obj.ObjParts;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** The crystal arch (baked OBJ frame) with the swirling frost vortex: three counter-rotating emissive layers. */
public class PortalRenderer implements BlockEntityRenderer<PortalBlockEntity> {
    public static final Identifier VORTEX = new Identifier(IceAgeYantra.MODID, "textures/entity/frost_vortex.png");
    private static final int FULL = LightmapTextureManager.MAX_LIGHT_COORDINATE;

    public PortalRenderer(BlockEntityRendererFactory.Context ctx) {
    }

    @Override
    public void render(PortalBlockEntity be, float pt, MatrixStack ps, VertexConsumerProvider buf, int light, int overlay) {
        float t = be.age + pt;
        float yaw = 180f - be.getCachedState().get(PortalBlock.FACING).asRotation();
        // frame
        ps.push();
        ps.translate(0.5, 0, 0.5);
        ps.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yaw));
        ps.translate(-0.5, 0, -0.5);
        ObjParts.PORTAL_FRAME.render(ps, buf, light, overlay);
        ps.pop();
        // vortex
        ps.push();
        ps.translate(0.5, 1.3, 0.5);
        ps.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yaw));
        ps.scale(1.3f, 1.3f, 1.3f);
        disc(buf.getBuffer(RenderLayer.getEntityTranslucent(VORTEX)), ps, 0.56f, 1.02f, t * 1.2f, 0.10f, 0.25f, 0.55f, 0.92f, 0.0f);
        VertexConsumer glow = buf.getBuffer(RenderLayer.getEntityTranslucentEmissive(VORTEX));
        disc(glow, ps, 0.55f, 1.0f, -t * 2.6f, 0.75f, 0.92f, 1.0f, 0.95f, 0.004f);
        disc(glow, ps, 0.47f, 0.86f, t * 4.1f, 0.55f, 0.85f, 1.0f, 0.75f, 0.008f);
        float pulse = 0.5f + 0.5f * MathHelper.sin(t * 0.08f);
        disc(glow, ps, 0.30f + 0.04f * pulse, 0.55f + 0.06f * pulse, -t * 7.0f, 1f, 1f, 1f, 0.55f, 0.012f);
        ps.pop();
        YantraRig.glow(ps, buf, 0.5, 1.35, 0.5, 3.3f, 0.35f, 0.75f, 1.0f, 0.35f + 0.15f * pulse);
    }

    /** Elliptical disc made of quads, textured with a rotating polar mapping, drawn on both faces. */
    private static void disc(VertexConsumer vc, MatrixStack ps, float rx, float ry, float rotDeg, float r, float g, float b, float a, float z) {
        Matrix4f m = ps.peek().getPositionMatrix();
        Matrix3f n = ps.peek().getNormalMatrix();
        int seg = 40;
        float rot = rotDeg * MathHelper.RADIANS_PER_DEGREE;
        for (int face = 0; face < 2; face++) {
            float zz = face == 0 ? z : -z;
            float nz = face == 0 ? 1 : -1;
            for (int i = 0; i < seg; i++) {
                float a0 = (float) (2 * Math.PI * i / seg), a1 = (float) (2 * Math.PI * (i + 1) / seg);
                float x0 = MathHelper.cos(a0) * rx, y0 = MathHelper.sin(a0) * ry, x1 = MathHelper.cos(a1) * rx, y1 = MathHelper.sin(a1) * ry;
                float u0 = 0.5f + 0.5f * MathHelper.cos(a0 + rot), v0 = 0.5f + 0.5f * MathHelper.sin(a0 + rot);
                float u1 = 0.5f + 0.5f * MathHelper.cos(a1 + rot), v1 = 0.5f + 0.5f * MathHelper.sin(a1 + rot);
                if (face == 0) {
                    put(vc, m, n, 0, 0, zz, 0.5f, 0.5f, r, g, b, a, nz);
                    put(vc, m, n, x0, y0, zz, u0, v0, r, g, b, a, nz);
                    put(vc, m, n, x1, y1, zz, u1, v1, r, g, b, a, nz);
                    put(vc, m, n, x1, y1, zz, u1, v1, r, g, b, a, nz);
                } else {
                    put(vc, m, n, 0, 0, zz, 0.5f, 0.5f, r, g, b, a, nz);
                    put(vc, m, n, x1, y1, zz, u1, v1, r, g, b, a, nz);
                    put(vc, m, n, x0, y0, zz, u0, v0, r, g, b, a, nz);
                    put(vc, m, n, x0, y0, zz, u0, v0, r, g, b, a, nz);
                }
            }
        }
    }

    private static void put(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float z, float u, float v, float r, float g, float b, float a, float nz) {
        vc.vertex(m, x, y, z).color(r, g, b, a).texture(u, v).overlay(OverlayTexture.DEFAULT_UV).light(FULL).normal(n, 0, 0, nz).next();
    }

    @Override
    public boolean rendersOutsideBoundingBox(PortalBlockEntity be) {
        return true;
    }

    @Override
    public int getRenderDistance() {
        return 128;
    }
}
'''),
    'src/main/java/com/iceage/yantra/client/render/ShowcaseRenderer.java': ('text', r'''package com.iceage.yantra.client.render;

import com.iceage.yantra.Tier;
import com.iceage.yantra.block.ShowcaseBlock;
import com.iceage.yantra.block.ShowcaseBlockEntity;
import com.iceage.yantra.client.ClientIceState;
import com.iceage.yantra.client.obj.ObjParts;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;

import java.util.Locale;

/** Ice Shop showcase (ported from the Forge showroom): carved pedestal, light beam, floating spinning Yantra, price tag. */
public class ShowcaseRenderer implements BlockEntityRenderer<ShowcaseBlockEntity> {
    private static final int FULL = LightmapTextureManager.MAX_LIGHT_COORDINATE;
    private final TextRenderer font;

    public ShowcaseRenderer(BlockEntityRendererFactory.Context ctx) {
        font = ctx.getTextRenderer();
    }

    @Override
    public void render(ShowcaseBlockEntity be, float pt, MatrixStack ps, VertexConsumerProvider buf, int light, int overlay) {
        Tier tier = Tier.byNumber(be.getCachedState().get(ShowcaseBlock.TIER));
        ObjParts.PEDESTAL.render(ps, buf, light, overlay);
        float time = be.age + pt;
        float bob = 0.06f * MathHelper.sin(time * 0.06f);
        boolean affordable = ClientIceState.points >= tier.price;
        int ov = OverlayTexture.DEFAULT_UV;
        float cr = ((tier.color >> 16) & 255) / 255f, cg = ((tier.color >> 8) & 255) / 255f, cb = (tier.color & 255) / 255f;

        YantraRig.beam(ps, buf, 0.5, 0.96, 0.5, tier == Tier.VAN ? 2.4f : 0.9f, 2.4f, cr * 0.6f + 0.2f, cg * 0.6f + 0.35f, 1f, 0.45f, time * 0.4f);

        ps.push();
        ps.translate(0.5, 1.12 + bob, 0.5);
        ps.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(time * 0.9f));
        switch (tier) {
            case GOLA -> {
                ps.scale(0.85f, 0.85f, 0.85f);
                ps.translate(-0.5, 0, -0.5);
            }
            case FAN -> {
                ps.scale(0.8f, 0.8f, 0.8f);
                ps.translate(-0.5, 0, -0.5);
            }
            case FRIDGE -> {
                ps.scale(0.62f, 0.62f, 0.62f);
                ps.translate(-0.5, 0, -0.5);
            }
            case AC -> {
                ps.scale(0.95f, 0.95f, 0.95f);
                ps.translate(-0.5, 0, -0.5);
            }
            case VAN -> ps.scale(0.5f, 0.5f, 0.5f);
        }
        YantraRig.tier(tier, ps, buf, FULL, ov, time);
        ps.pop();
        float top = switch (tier) {
            case GOLA, FAN -> 2.35f;
            case FRIDGE -> 2.6f;
            case AC -> 2.45f;
            case VAN -> 3.65f;
        };
        YantraRig.glow(ps, buf, 0.5, 1.6 + bob, 0.5, tier == Tier.VAN ? 4.2f : 2.0f, cr, cg, cb, 0.28f);

        // price tag (billboard)
        ps.push();
        ps.translate(0.5, top + bob, 0.5);
        ps.multiply(MinecraftClient.getInstance().getEntityRenderDispatcher().getRotation());
        ps.scale(-0.022f, -0.022f, 0.022f);
        Matrix4f m = ps.peek().getPositionMatrix();
        String price = tier.price == 0 ? "FREE" : String.format(Locale.US, "%,d", tier.price) + " BLOCKS FROZEN";
        String status;
        int statusColor;
        if (affordable) {
            status = "RIGHT-CLICK TO TAKE";
            statusColor = 0xFFE27A;
        } else {
            status = "LOCKED - " + String.format(Locale.US, "%,d", tier.price - ClientIceState.points) + " MORE";
            statusColor = 0xFF6B6B;
        }
        float pulse = 0.75f + 0.25f * MathHelper.sin(time * 0.2f);
        int sc = ((int) (((statusColor >> 16) & 255) * pulse) << 16) | ((int) (((statusColor >> 8) & 255) * pulse) << 8) | (int) ((statusColor & 255) * pulse);
        line(m, buf, "- TIER " + tier.number + " -", -30, 0xBFEFFF, 0x66000000);
        line(m, buf, tier.displayName, -18, tier.color, 0x88001A3A);
        line(m, buf, price, -6, 0x9FF0FF, 0x66000000);
        line(m, buf, status, 8, sc, 0x77000000);
        ps.pop();
    }

    private void line(Matrix4f m, VertexConsumerProvider buf, String s, int y, int color, int bg) {
        float x = -font.getWidth(s) / 2f;
        font.draw(Text.literal(s), x, y, 0xFF000000 | color, false, m, buf, TextRenderer.TextLayerType.NORMAL, bg, FULL);
    }

    @Override
    public boolean rendersOutsideBoundingBox(ShowcaseBlockEntity be) {
        return true;
    }

    @Override
    public int getRenderDistance() {
        return 96;
    }
}
'''),
    'src/main/java/com/iceage/yantra/client/render/VanRenderer.java': ('text', r'''package com.iceage.yantra.client.render;

import com.iceage.yantra.entity.IceVanEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

/** Renders the Musical Ice Cream Van with the same baked OBJ rig as the Forge version, 1.4x bigger. */
public class VanRenderer extends EntityRenderer<IceVanEntity> {
    public VanRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
        shadowRadius = 1.8f;
    }

    @Override
    public void render(IceVanEntity e, float yaw, float tickDelta, MatrixStack ps, VertexConsumerProvider buf, int light) {
        ps.push();
        float y = MathHelper.lerpAngleDegrees(tickDelta, e.prevYaw, e.getYaw());
        ps.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-y));
        float lean = MathHelper.lerp(tickDelta, e.prevLean, e.lean);
        ps.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(lean));
        float bounce = Math.abs(e.groundSpeed) > 0.05f ? 0.015f * MathHelper.sin((e.age + tickDelta) * 1.7f) : 0f;
        ps.translate(0, bounce, 0);
        ps.scale(IceVanEntity.SCALE, IceVanEntity.SCALE, IceVanEntity.SCALE);
        float wheel = MathHelper.lerp(tickDelta, e.prevWheelAngle, e.wheelAngle);
        float steer = MathHelper.lerp(tickDelta, e.prevSteer, e.steer);
        YantraRig.van(ps, buf, light, OverlayTexture.DEFAULT_UV, wheel, steer, e.age + tickDelta, true);
        ps.pop();
        super.render(e, yaw, tickDelta, ps, buf, light);
    }

    @Override
    public Identifier getTexture(IceVanEntity entity) {
        return SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE;
    }
}
'''),
    'src/main/java/com/iceage/yantra/client/render/YantraRenderer.java': ('text', r'''package com.iceage.yantra.client.render;

import com.iceage.yantra.Tier;
import com.iceage.yantra.block.YantraBlock;
import com.iceage.yantra.block.YantraBlockEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;

/** One renderer for the four placeable Yantras: draws the baked OBJ rig and animates it while it runs. */
public class YantraRenderer implements BlockEntityRenderer<YantraBlockEntity> {
    public YantraRenderer(BlockEntityRendererFactory.Context ctx) {
    }

    @Override
    public void render(YantraBlockEntity be, float pt, MatrixStack ps, VertexConsumerProvider buf, int light, int overlay) {
        if (!(be.getCachedState().getBlock() instanceof YantraBlock yb)) return;
        Tier tier = yb.tier;
        ps.push();
        YantraRig.faceBlock(ps, be.getCachedState().get(YantraBlock.FACING));
        float angle = MathHelper.lerp(pt, be.prevAngle, be.angle);
        float run = Math.min(1f, be.speed / 40f);
        float time = be.age + pt;
        switch (tier) {
            case GOLA -> YantraRig.gola(ps, buf, light, overlay, angle, 0, 0);
            case FAN -> YantraRig.fan(ps, buf, light, overlay, angle, 22f * run * MathHelper.sin(time * 0.05f), run, time);
            case FRIDGE -> {
                float door = Math.max(0f, MathHelper.lerp(pt, be.prevDoor, be.door));
                YantraRig.fridge(ps, buf, light, overlay, door, door * 0.66f);
            }
            case AC -> YantraRig.ac(ps, buf, light, overlay, angle);
            default -> {
            }
        }
        ps.pop();
    }

    @Override
    public boolean rendersOutsideBoundingBox(YantraBlockEntity be) {
        return true;
    }

    @Override
    public int getRenderDistance() {
        return 96;
    }
}
'''),
    'src/main/java/com/iceage/yantra/client/render/YantraRig.java': ('text', r'''package com.iceage.yantra.client.render;

import com.iceage.yantra.IceAgeYantra;
import com.iceage.yantra.RigData;
import com.iceage.yantra.Tier;
import com.iceage.yantra.client.obj.ObjParts;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * Poses and draws every Yantra rig (ported 1:1 from the Forge version). Block rigs live in 0..1 block space facing
 * north; the van is centred and faces +Z.
 */
public final class YantraRig {
    public static final Identifier HALO = new Identifier(IceAgeYantra.MODID, "textures/entity/glow_halo.png");
    public static final Identifier BEAM = new Identifier(IceAgeYantra.MODID, "textures/entity/light_beam.png");
    private static final int FULL = LightmapTextureManager.MAX_LIGHT_COORDINATE;

    private YantraRig() {
    }

    // ------------------------------------------------------------------------------------------------ gola
    /** @param handleState 0 = attached, 1 = flying (t = handleT 0..1), 2 = lying on the ground */
    public static void gola(MatrixStack ps, VertexConsumerProvider buf, int light, int overlay, float wheelAngle, int handleState, float handleT) {
        ObjParts.GOLA_BODY.render(ps, buf, light, overlay);
        ps.push();
        pivotY(ps, RigData.GOLA_WHEEL_X, RigData.GOLA_WHEEL_Y, RigData.GOLA_WHEEL_Z, wheelAngle);
        ObjParts.GOLA_WHEEL.render(ps, buf, light, overlay);
        if (handleState == 0) ObjParts.GOLA_HANDLE.render(ps, buf, light, overlay);
        ps.pop();
        if (handleState != 0) {
            double hx = RigData.GOLA_HANDLE_TOP_X, hy = RigData.GOLA_HANDLE_TOP_Y - 0.26, hz = RigData.GOLA_HANDLE_TOP_Z;
            double ex = hx + 0.95, ey = 0.035, ez = hz - 0.25;
            float t = handleState == 1 ? MathHelper.clamp(handleT, 0f, 1f) : 1f;
            double px = MathHelper.lerp(t, hx, ex), pz = MathHelper.lerp(t, hz, ez);
            double py = MathHelper.lerp(t, hy, ey) + 1.5 * 4.0 * t * (1.0 - t);
            ps.push();
            ps.translate(px, py, pz);
            ps.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-25f * t));
            ps.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-90f - 720f * (1f - t) * (handleState == 1 ? 1f : 0f)));
            ps.multiply(RotationAxis.POSITIVE_X.rotationDegrees(handleState == 1 ? 540f * (1f - t) : 0f));
            ps.translate(-hx, -hy, -hz);
            ObjParts.GOLA_HANDLE.render(ps, buf, light, overlay);
            ps.pop();
        }
    }

    // ------------------------------------------------------------------------------------------------- fan
    public static void fan(MatrixStack ps, VertexConsumerProvider buf, int light, int overlay, float bladeAngle, float swing, float shake, float time) {
        ObjParts.FAN_BODY.render(ps, buf, light, overlay);
        ps.push();
        float jx = shake * 0.012f * MathHelper.sin(time * 2.9f), jy = shake * 0.008f * MathHelper.sin(time * 3.7f + 1f);
        ps.translate(jx, jy, 0);
        pivotY(ps, RigData.FAN_NECK_X, RigData.FAN_NECK_Y, RigData.FAN_NECK_Z, swing);
        ObjParts.FAN_HEAD.render(ps, buf, light, overlay);
        ps.translate(RigData.FAN_ROTOR_X, RigData.FAN_ROTOR_Y, RigData.FAN_ROTOR_Z);
        ps.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(bladeAngle));
        ps.translate(-RigData.FAN_ROTOR_X, -RigData.FAN_ROTOR_Y, -RigData.FAN_ROTOR_Z);
        ObjParts.FAN_ROTOR.render(ps, buf, light, overlay);
        ps.pop();
    }

    // ---------------------------------------------------------------------------------------------- fridge
    public static void fridge(MatrixStack ps, VertexConsumerProvider buf, int light, int overlay, float door, float topDoor) {
        ObjParts.FRIDGE_BODY.render(ps, buf, light, overlay);
        ps.push();
        pivotY(ps, RigData.FRIDGE_HINGE_X, 0, RigData.FRIDGE_HINGE_Z, door);
        ObjParts.FRIDGE_DOOR_BOTTOM.render(ps, buf, light, overlay);
        ps.pop();
        ps.push();
        pivotY(ps, RigData.FRIDGE_HINGE_X, 0, RigData.FRIDGE_HINGE_Z, topDoor);
        ObjParts.FRIDGE_DOOR_TOP.render(ps, buf, light, overlay);
        ps.pop();
    }

    // -------------------------------------------------------------------------------------------------- ac
    public static void ac(MatrixStack ps, VertexConsumerProvider buf, int light, int overlay, float fanAngle) {
        ObjParts.AC_BODY.render(ps, buf, light, overlay);
        ps.push();
        ps.translate(RigData.AC_ROTOR_X, RigData.AC_ROTOR_Y, RigData.AC_ROTOR_Z);
        ps.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(fanAngle));
        ps.translate(-RigData.AC_ROTOR_X, -RigData.AC_ROTOR_Y, -RigData.AC_ROTOR_Z);
        ObjParts.AC_ROTOR.render(ps, buf, light, overlay);
        ps.pop();
    }

    // ------------------------------------------------------------------------------------------------- van
    public static void van(MatrixStack ps, VertexConsumerProvider buf, int light, int overlay, float wheelAngle, float steer, float time, boolean halo) {
        ObjParts.TRUCK_BODY.render(ps, buf, light, overlay);
        ObjParts.TRUCK_DECALS.render(ps, buf, light, overlay);
        wheel(ps, buf, ObjParts.TRUCK_WHEEL_FL, RigData.TRUCK_WHEEL_FL_X, RigData.TRUCK_WHEEL_FL_Y, RigData.TRUCK_WHEEL_FL_Z, wheelAngle, steer, light, overlay);
        wheel(ps, buf, ObjParts.TRUCK_WHEEL_FR, RigData.TRUCK_WHEEL_FR_X, RigData.TRUCK_WHEEL_FR_Y, RigData.TRUCK_WHEEL_FR_Z, wheelAngle, steer, light, overlay);
        wheel(ps, buf, ObjParts.TRUCK_WHEEL_RL, RigData.TRUCK_WHEEL_RL_X, RigData.TRUCK_WHEEL_RL_Y, RigData.TRUCK_WHEEL_RL_Z, wheelAngle, 0, light, overlay);
        wheel(ps, buf, ObjParts.TRUCK_WHEEL_RR, RigData.TRUCK_WHEEL_RR_X, RigData.TRUCK_WHEEL_RR_Y, RigData.TRUCK_WHEEL_RR_Z, wheelAngle, 0, light, overlay);
        // tinted see-through windscreen + windows (the driver can see out in first person)
        ObjParts.TRUCK_GLASS.render(ps, buf, light, overlay, 0.85f, 0.95f, 1.0f, 0.42f);
        // the glowing giant ice cream cone (full bright, gentle spin + pulse)
        float pulse = 0.88f + 0.12f * MathHelper.sin(time * 0.15f);
        ps.push();
        ps.translate(RigData.TRUCK_CONE_X, RigData.TRUCK_CONE_Y, RigData.TRUCK_CONE_Z);
        ps.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(time * 2.0f));
        ps.translate(-RigData.TRUCK_CONE_X, -RigData.TRUCK_CONE_Y, -RigData.TRUCK_CONE_Z);
        ObjParts.TRUCK_CONE.render(ps, buf, FULL, overlay, pulse, pulse, pulse, 1f);
        ps.pop();
        if (halo) {
            glow(ps, buf, RigData.TRUCK_CONE_X, RigData.TRUCK_CONE_Y + 0.9, RigData.TRUCK_CONE_Z, 1.9f * pulse, 1.0f, 0.62f, 0.86f, 0.55f);
        }
    }

    private static void wheel(MatrixStack ps, VertexConsumerProvider buf, ObjParts.Part part, double x, double y, double z, float spin, float steer,
            int light, int overlay) {
        ps.push();
        ps.translate(x, y, z);
        if (steer != 0) ps.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(steer));
        ps.multiply(RotationAxis.POSITIVE_X.rotationDegrees(spin));
        ps.translate(-x, -y, -z);
        part.render(ps, buf, light, overlay);
        ps.pop();
    }

    // -------------------------------------------------------------------------------------------- generic
    public static void tier(Tier t, MatrixStack ps, VertexConsumerProvider buf, int light, int overlay, float time) {
        switch (t) {
            case GOLA -> gola(ps, buf, light, overlay, time * 6f, 0, 0);
            case FAN -> fan(ps, buf, light, overlay, time * 25f, 25f * MathHelper.sin(time * 0.05f), 0, time);
            case FRIDGE -> fridge(ps, buf, light, overlay, 0, 0);
            case AC -> ac(ps, buf, light, overlay, time * 12f);
            case VAN -> van(ps, buf, light, overlay, time * 10f, 0, time, false);
        }
    }

    private static void pivotY(MatrixStack ps, double x, double y, double z, float deg) {
        ps.translate(x, y, z);
        ps.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(deg));
        ps.translate(-x, -y, -z);
    }

    /** Camera-facing additive glow sprite. */
    public static void glow(MatrixStack ps, VertexConsumerProvider buf, double x, double y, double z, float size, float r, float g, float b, float a) {
        ps.push();
        ps.translate(x, y, z);
        Quaternionf cam = MinecraftClient.getInstance().getEntityRenderDispatcher().getRotation();
        ps.multiply(cam);
        float s = size / 2f;
        VertexConsumer vc = buf.getBuffer(RenderLayer.getEntityTranslucentEmissive(HALO));
        Matrix4f m = ps.peek().getPositionMatrix();
        Matrix3f n = ps.peek().getNormalMatrix();
        int ov = OverlayTexture.DEFAULT_UV;
        vc.vertex(m, -s, -s, 0).color(r, g, b, a).texture(0, 1).overlay(ov).light(FULL).normal(n, 0, 0, 1).next();
        vc.vertex(m, s, -s, 0).color(r, g, b, a).texture(1, 1).overlay(ov).light(FULL).normal(n, 0, 0, 1).next();
        vc.vertex(m, s, s, 0).color(r, g, b, a).texture(1, 0).overlay(ov).light(FULL).normal(n, 0, 0, 1).next();
        vc.vertex(m, -s, s, 0).color(r, g, b, a).texture(0, 0).overlay(ov).light(FULL).normal(n, 0, 0, 1).next();
        ps.pop();
    }

    /** Soft vertical light beam (two crossed additive quads). */
    public static void beam(MatrixStack ps, VertexConsumerProvider buf, double x, double y, double z, float w, float h, float r, float g, float b, float a, float rot) {
        VertexConsumer vc = buf.getBuffer(RenderLayer.getEntityTranslucentEmissive(BEAM));
        int ov = OverlayTexture.DEFAULT_UV;
        for (int k = 0; k < 2; k++) {
            ps.push();
            ps.translate(x, y, z);
            ps.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(rot + k * 90f));
            Matrix4f m = ps.peek().getPositionMatrix();
            Matrix3f n = ps.peek().getNormalMatrix();
            float s = w / 2f;
            for (int side = 0; side < 2; side++) {
                float x0 = side == 0 ? -s : s, x1 = side == 0 ? s : -s;
                vc.vertex(m, x0, 0, 0).color(r, g, b, a).texture(0, 1).overlay(ov).light(FULL).normal(n, 0, 1, 0).next();
                vc.vertex(m, x1, 0, 0).color(r, g, b, a).texture(1, 1).overlay(ov).light(FULL).normal(n, 0, 1, 0).next();
                vc.vertex(m, x1, h, 0).color(r, g, b, 0).texture(1, 0).overlay(ov).light(FULL).normal(n, 0, 1, 0).next();
                vc.vertex(m, x0, h, 0).color(r, g, b, 0).texture(0, 0).overlay(ov).light(FULL).normal(n, 0, 1, 0).next();
            }
            ps.pop();
        }
    }

    /** Rotates a block-space (0..1) rig to face the given block facing around the block centre. */
    public static void faceBlock(MatrixStack ps, Direction facing) {
        ps.translate(0.5, 0, 0.5);
        ps.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180f - facing.asRotation()));
        ps.translate(-0.5, 0, -0.5);
    }
}
'''),
    'src/main/java/com/iceage/yantra/entity/GotiyaEntity.java': ('text', r'''package com.iceage.yantra.entity;

import com.iceage.yantra.FreezeEngine;
import com.iceage.yantra.ModEntities;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.NoPenaltyTargeting;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.village.VillagerData;
import net.minecraft.village.VillagerDataContainer;
import net.minecraft.village.VillagerProfession;
import net.minecraft.village.VillagerType;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * Gotiya: a custom villager NPC with a bold aqua name tag. During the Ice Cream Van climax he stands in the van's
 * path holding an oversized industrial hairdryer and blasts flames and hot air at the ice, melting the frozen road
 * back into water. Hit him and he panics (walk speed 0.5).
 */
public class GotiyaEntity extends PathAwareEntity implements VillagerDataContainer {
    public static final int IDLE = 0, DRYER = 1, FLEEING = 2;
    /** Normal panic walk speed. */
    public static final float PANIC_SPEED = 0.5F;
    private static final TrackedData<Integer> STATE = DataTracker.registerData(GotiyaEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<VillagerData> VDATA = DataTracker.registerData(GotiyaEntity.class, TrackedDataHandlerRegistry.VILLAGER_DATA);

    private int stateTicks;
    private boolean climax;
    @Nullable
    private Vec3d threat;

    public GotiyaEntity(EntityType<? extends GotiyaEntity> type, World world) {
        super(type, world);
        setPersistent();
        applyName();
    }

    private void applyName() {
        // §b§lGotiya
        setCustomName(Text.literal("Gotiya").formatted(Formatting.AQUA, Formatting.BOLD));
        setCustomNameVisible(true);
    }

    public static DefaultAttributeContainer.Builder createGotiyaAttributes() {
        return PathAwareEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 20.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.5)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 64.0);
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        dataTracker.startTracking(STATE, IDLE);
        dataTracker.startTracking(VDATA, new VillagerData(VillagerType.DESERT, VillagerProfession.FARMER, 1));
    }

    @Override
    public VillagerData getVillagerData() {
        return dataTracker.get(VDATA);
    }

    @Override
    public void setVillagerData(VillagerData data) {
        dataTracker.set(VDATA, data);
    }

    public int state() {
        return dataTracker.get(STATE);
    }

    public boolean hasHairdryer() {
        return state() == DRYER;
    }

    private void setState(int s) {
        dataTracker.set(STATE, s);
        stateTicks = 0;
    }

    @Override
    protected void initGoals() {
        goalSelector.add(0, new SwimGoal(this));
        goalSelector.add(1, new FleeGoal());
        goalSelector.add(8, new LookAtEntityGoal(this, PlayerEntity.class, 8.0f));
    }

    /** Spawns Gotiya with his hairdryer at the given position (snapped to the ground). */
    @Nullable
    public static GotiyaEntity spawnClimax(ServerWorld world, Vec3d at, @Nullable LivingEntity target) {
        int x = MathHelper.floor(at.x), z = MathHelper.floor(at.z);
        if (!world.isChunkLoaded(x >> 4, z >> 4)) return null;
        int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
        GotiyaEntity g = ModEntities.GOTIYA.create(world);
        if (g == null) return null;
        g.refreshPositionAndAngles(x + 0.5, y, z + 0.5, 0f, 0f);
        g.climax = true;
        g.initialize(world, world.getLocalDifficulty(g.getBlockPos()), SpawnReason.EVENT, null, null);
        g.applyName();
        if (target != null) g.threat = target.getPos();
        g.setState(DRYER);
        world.spawnEntity(g);
        world.spawnParticles(ParticleTypes.POOF, x + 0.5, y + 0.5, z + 0.5, 14, 0.3, 0.5, 0.3, 0.02);
        return g;
    }

    /** The thing Gotiya wants to melt a path towards: the closest driven van, otherwise the closest player. */
    @Nullable
    private Vec3d findTarget() {
        IceVanEntity best = null;
        double bd = 100 * 100;
        for (IceVanEntity v : getWorld().getEntitiesByClass(IceVanEntity.class, getBoundingBox().expand(100), e -> true)) {
            double d = v.squaredDistanceTo(this);
            if (d < bd) {
                bd = d;
                best = v;
            }
        }
        if (best != null) return best.getPos();
        PlayerEntity p = getWorld().getClosestPlayer(this, 100);
        return p != null ? p.getPos() : null;
    }

    @Override
    public void tick() {
        super.tick();
        stateTicks++;
        if (getWorld().isClient) {
            clientFx();
            return;
        }
        ServerWorld sw = (ServerWorld) getWorld();
        switch (state()) {
            case DRYER -> tickDryer(sw);
            case FLEEING -> {
                if (stateTicks % 25 == 5) playSound(SoundEvents.ENTITY_VILLAGER_HURT, 1.0f, 1.0f + random.nextFloat() * 0.3f);
                if (stateTicks >= 160) {
                    Vec3d t = climax ? findTarget() : null;
                    if (t != null) {
                        setState(DRYER);
                    } else {
                        sw.spawnParticles(ParticleTypes.POOF, getX(), getY() + 0.8, getZ(), 18, 0.3, 0.6, 0.3, 0.02);
                        discard();
                    }
                }
            }
            default -> {
                if (stateTicks > 6000) discard();
            }
        }
    }

    /** The climax attack: hot air and flames toward the ice, melting a cone of the frozen road. */
    private void tickDryer(ServerWorld sw) {
        Vec3d target = findTarget();
        if (target == null) {
            if (stateTicks > 400) discard();
            return;
        }
        getNavigation().stop();
        Vec3d to = target.subtract(getPos());
        double hd = Math.sqrt(to.x * to.x + to.z * to.z);
        if (hd < 1.0e-3) return;
        double dx = to.x / hd, dz = to.z / hd;
        float yaw = (float) (MathHelper.atan2(-dx, dz) * MathHelper.DEGREES_PER_RADIAN);
        setYaw(yaw);
        setBodyYaw(yaw);
        setHeadYaw(yaw);

        // the nozzle of the (oversized) hairdryer
        double nx = getX() + dx * 4.0, ny = getY() + 4.4, nz = getZ() + dz * 4.0;   // mouth of the giant hairdryer
        for (int i = 0; i < 6; i++) {
            double sp = 0.7 + random.nextDouble() * 0.5;
            sw.spawnParticles(ParticleTypes.FLAME, nx, ny, nz, 0, dx + (random.nextDouble() - 0.5) * 0.35, -0.18 + (random.nextDouble() - 0.5) * 0.25,
                    dz + (random.nextDouble() - 0.5) * 0.35, sp);
        }
        if (stateTicks % 2 == 0) {
            sw.spawnParticles(ParticleTypes.LARGE_SMOKE, nx, ny, nz, 0, dx * 0.8, -0.05, dz * 0.8, 0.5);
            sw.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, nx, ny, nz, 0, dx * 0.6, 0.03, dz * 0.6, 0.4);
        }
        if (stateTicks % 20 == 0) {
            sw.playSound(null, getBlockPos(), SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.HOSTILE, 1.0f, 0.5f);
            sw.playSound(null, getBlockPos(), SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.HOSTILE, 0.8f, 0.6f);
        }
        if (stateTicks % 4 == 0) {
            // melt a widening cone: frozen blocks become (briefly flowing) water, the ground stays
            double px = -dz, pz = dx;
            int melted = 0;
            for (int t = 2; t <= 16 && melted < 18; t++) {
                int half = Math.max(1, (int) Math.round(t * 0.22));
                for (int l = -half; l <= half; l++) {
                    int x = MathHelper.floor(getX() + dx * t + px * l);
                    int z = MathHelper.floor(getZ() + dz * t + pz * l);
                    melted += FreezeEngine.meltColumn(sw, x, z, MathHelper.floor(getY()));
                }
            }
            if (melted > 0) {
                sw.spawnParticles(ParticleTypes.CLOUD, getX() + dx * 7, getY() + 0.6, getZ() + dz * 7, 8, 2.0, 0.3, 2.0, 0.02);
                sw.playSound(null, getBlockPos(), SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.HOSTILE, 0.5f, 1.1f);
            }
        }
        if (stateTicks % 40 == 10) playSound(SoundEvents.ENTITY_VILLAGER_AMBIENT, 0.9f, 0.7f);
    }

    /** Scream and run away at normal panic speed. */
    public void flee(@Nullable PlayerEntity by) {
        if (state() == FLEEING) return;
        threat = by != null ? by.getPos() : getPos().add(random.nextGaussian(), 0, random.nextGaussian());
        setState(FLEEING);
        playSound(SoundEvents.ENTITY_VILLAGER_HURT, 1.4f, 1.0f);
        Vec3d away = getPos().subtract(threat).multiply(1, 0, 1);
        if (away.lengthSquared() < 0.01) away = new Vec3d(1, 0, 0);
        away = away.normalize();
        setVelocity(away.x * 0.2, 0.35, away.z * 0.2);
        velocityModified = true;
        if (getWorld() instanceof ServerWorld sw) {
            sw.spawnParticles(ParticleTypes.SPLASH, getX(), getY() + 1.8, getZ(), 18, 0.3, 0.3, 0.3, 0.2);
            sw.spawnParticles(ParticleTypes.ANGRY_VILLAGER, getX(), getY() + 2.0, getZ(), 3, 0.3, 0.2, 0.3, 0.0);
        }
    }

    private void clientFx() {
        if (state() != FLEEING && random.nextInt(4) == 0) {
            getWorld().addParticle(ParticleTypes.FALLING_WATER, getX() + (random.nextDouble() - 0.5) * 0.5, getY() + 1.9,
                    getZ() + (random.nextDouble() - 0.5) * 0.5, 0, 0, 0);
        }
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.damage(source, amount);
        if (!getWorld().isClient && source.getAttacker() instanceof PlayerEntity p) flee(p);
        return false;
    }

    @Override
    public boolean isPushable() {
        return state() != DRYER;
    }

    @Override
    public boolean canImmediatelyDespawn(double distanceSquared) {
        return false;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENTITY_VILLAGER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENTITY_VILLAGER_DEATH;
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putInt("gotiyaState", state());
        nbt.putBoolean("climax", climax);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        climax = nbt.getBoolean("climax");
        int s = nbt.getInt("gotiyaState");
        dataTracker.set(STATE, s == FLEEING ? FLEEING : (s == DRYER ? DRYER : IDLE));
        applyName();
    }

    private class FleeGoal extends Goal {
        FleeGoal() {
            setControls(EnumSet.of(Goal.Control.MOVE, Goal.Control.LOOK));
        }

        @Override
        public boolean canStart() {
            return state() == FLEEING;
        }

        @Override
        public void tick() {
            if (getNavigation().isIdle() && threat != null) {
                Vec3d target = NoPenaltyTargeting.findFrom(GotiyaEntity.this, 24, 7, threat);
                if (target != null) getNavigation().startMovingTo(target.x, target.y, target.z, PANIC_SPEED);
            }
        }
    }
}
'''),
    'src/main/java/com/iceage/yantra/entity/IceVanEntity.java': ('text', r'''package com.iceage.yantra.entity;

import com.iceage.yantra.FreezeEngine;
import com.iceage.yantra.IceAgeYantra;
import com.iceage.yantra.IcePoints;
import com.iceage.yantra.ModEntities;
import com.iceage.yantra.ModItems;
import com.iceage.yantra.ModParticles;
import com.iceage.yantra.Tier;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.thrown.SnowballEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.particle.ItemStackParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * The Musical Ice Cream Van: a real custom vehicle entity (not a pig, not a vanilla mob). WASD driving with smooth
 * turning, the driver sits inside the cab, it lays a packed-ice road and fires snowballs and spinning ice
 * particles to its left and right. Driving it triggers Gotiya's hairdryer climax.
 */
public class IceVanEntity extends Entity {
    /** Visual and seat scale of the rig model. */
    public static final float SCALE = 1.4f;
    /** Set by the client mod: keeps the jingle playing while the van is driven (kept out of common code). */
    public static java.util.function.Consumer<IceVanEntity> jingleHook = v -> {
    };

    private static final TrackedData<Integer> COMBO = DataTracker.registerData(IceVanEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final double MAX_SPEED = 0.62;
    private static final int MAX_COMBO = 20;
    private static final int COMBO_RAMP_SECONDS = 40;
    private static final int POINTS_PER_BLOCK = 30;
    private static final int ROAD_HALF_WIDTH = 3;

    private float speed;
    private int lerpSteps;
    private double lerpX, lerpY, lerpZ, lerpYaw;
    @Nullable
    private UUID owner;
    @Nullable
    private Vec3d lastServerPos;
    private int drivingTicks, idleTicks;
    private int climaxCooldown;
    @Nullable
    private UUID gotiya;

    // client-side animation state
    public float wheelAngle, prevWheelAngle, steer, prevSteer, lean, prevLean, groundSpeed;
    private float lastYaw;

    public IceVanEntity(EntityType<? extends IceVanEntity> type, World world) {
        super(type, world);
        setStepHeight(1.1f);
    }

    public void setOwner(UUID id) {
        owner = id;
    }

    @Override
    protected void initDataTracker() {
        dataTracker.startTracking(COMBO, 1);
    }

    public int combo() {
        return dataTracker.get(COMBO);
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        owner = nbt.containsUuid("owner") ? nbt.getUuid("owner") : null;
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        if (owner != null) nbt.putUuid("owner", owner);
    }

    @Override
    public Packet<ClientPlayPacketListener> createSpawnPacket() {
        return new EntitySpawnS2CPacket(this);
    }

    @Override
    public boolean canHit() {
        return !isRemoved();
    }

    @Override
    public boolean isCollidable() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public ItemStack getPickBlockStack() {
        return new ItemStack(ModItems.ICE_CREAM_VAN);
    }

    @Override
    public Box getVisibilityBoundingBox() {
        return getBoundingBox().expand(2.8, 3.2, 2.8);
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (isInvulnerableTo(source)) return false;
        if (getWorld().isClient || isRemoved()) return true;
        if (source.getAttacker() instanceof PlayerEntity p) {
            if (p.isSneaking() || p.getAbilities().creativeMode) {
                if (!p.getAbilities().creativeMode) dropStack(new ItemStack(ModItems.ICE_CREAM_VAN));
                removeAllPassengers();
                discard();
            } else {
                getWorld().playSound(null, getBlockPos(), com.iceage.yantra.ModSounds.VAN_HONK, SoundCategory.NEUTRAL, 1.6f, 1.0f);
            }
            return true;
        }
        return false;
    }

    @Override
    public ActionResult interact(PlayerEntity player, Hand hand) {
        if (player.shouldCancelInteraction()) return ActionResult.PASS;
        if (!getWorld().isClient) {
            if (getPassengerList().isEmpty() && player.startRiding(this)) {
                if (owner == null) owner = player.getUuid();
                return ActionResult.CONSUME;
            }
            return ActionResult.PASS;
        }
        return ActionResult.SUCCESS;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengerList().isEmpty() && passenger instanceof PlayerEntity;
    }

    @Nullable
    @Override
    public LivingEntity getControllingPassenger() {
        Entity e = getFirstPassenger();
        return e instanceof PlayerEntity p ? p : null;
    }

    @Override
    protected void updatePassengerPosition(Entity passenger, PositionUpdater updater) {
        if (!hasPassenger(passenger)) return;
        double lx = com.iceage.yantra.RigData.TRUCK_SEAT_X * SCALE, lz = com.iceage.yantra.RigData.TRUCK_SEAT_Z * SCALE;
        float yaw = getYaw() * MathHelper.RADIANS_PER_DEGREE;
        double ox = lx * MathHelper.cos(yaw) - lz * MathHelper.sin(yaw);
        double oz = lx * MathHelper.sin(yaw) + lz * MathHelper.cos(yaw);
        updater.accept(passenger, getX() + ox, getY() + com.iceage.yantra.RigData.TRUCK_SEAT_Y * SCALE + passenger.getHeightOffset(), getZ() + oz);
    }

    @Override
    public double getMountedHeightOffset() {
        return com.iceage.yantra.RigData.TRUCK_SEAT_Y * SCALE;
    }

    @Override
    public Vec3d updatePassengerForDismount(LivingEntity passenger) {
        float yaw = getYaw() * MathHelper.RADIANS_PER_DEGREE;
        for (double side : new double[]{2.6, -2.6}) {
            double ox = side * MathHelper.cos(yaw);
            double oz = side * MathHelper.sin(yaw);
            Vec3d p = new Vec3d(getX() + ox, getY() + 0.1, getZ() + oz);
            if (getWorld().isSpaceEmpty(passenger, passenger.getBoundingBox().offset(p.subtract(passenger.getPos())))) return p;
        }
        return new Vec3d(getX(), getBoundingBox().maxY + 0.1, getZ());
    }

    @Override
    public void tick() {
        super.tick();
        tickLerp();
        float yawBefore = getYaw();
        if (isLogicalSideForUpdatingMovement()) {
            drive();
            if (!isOnGround()) {
                setVelocity(getVelocity().add(0, -0.08, 0));
            } else if (getVelocity().y < 0) {
                setVelocity(getVelocity().multiply(1, 0, 1));
            }
            move(MovementType.SELF, getVelocity());
            if (horizontalCollision && Math.abs(speed) > 0.05f) speed *= 0.5f;
        } else {
            setVelocity(Vec3d.ZERO);
        }
        float dYaw = MathHelper.wrapDegrees(getYaw() - yawBefore);
        if (dYaw != 0) {
            for (Entity p : getPassengerList()) {
                p.setYaw(p.getYaw() + dYaw);
                p.setHeadYaw(p.getHeadYaw() + dYaw);
            }
        }
        if (getWorld().isClient) {
            clientAnim();
        } else {
            serverTick((ServerWorld) getWorld());
        }
    }

    private void drive() {
        LivingEntity c = getControllingPassenger();
        float fwd = 0, strafe = 0;
        if (c instanceof PlayerEntity p) {
            fwd = p.forwardSpeed;
            strafe = p.sidewaysSpeed;
        }
        float max = (float) MAX_SPEED;
        if (fwd > 0.01f) {
            speed += speed < 0 ? 0.05f : 0.018f + 0.01f * (1f - speed / max);
        } else if (fwd < -0.01f) {
            speed -= speed > 0 ? 0.045f : 0.012f;
        } else {
            speed *= 0.955f;
        }
        speed = MathHelper.clamp(speed, -max * 0.35f, max);
        if (Math.abs(speed) < 0.002f) speed = 0;
        float grip = Math.min(1f, Math.abs(speed) / 0.06f);
        float turn = strafe * (2.4f + 1.4f * Math.min(1f, Math.abs(speed) / max)) * grip * (speed >= 0 ? 1 : -1);
        setYaw(getYaw() - turn);
        float yaw = getYaw() * MathHelper.RADIANS_PER_DEGREE;
        setVelocity(-MathHelper.sin(yaw) * speed, getVelocity().y, MathHelper.cos(yaw) * speed);
    }

    // ------------------------------------------------------------------ server: road, points, side cannons, climax
    private void serverTick(ServerWorld world) {
        LivingEntity driver = getControllingPassenger();
        Vec3d now = getPos();
        double moved = lastServerPos == null ? 0 : Math.sqrt(Math.pow(now.x - lastServerPos.x, 2) + Math.pow(now.z - lastServerPos.z, 2));
        lastServerPos = now;

        if (driver != null) climax(world, driver);

        if (driver == null || moved < 0.015) {
            idleTicks++;
            if (idleTicks > 40) drivingTicks = Math.max(0, drivingTicks - 4);
            dataTracker.set(COMBO, comboFor(drivingTicks));
            return;
        }
        idleTicks = 0;
        drivingTicks++;
        int combo = comboFor(drivingTicks);
        dataTracker.set(COMBO, combo);

        double yaw = Math.toRadians(getYaw());
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);
        double lx = Math.cos(yaw), lz = Math.sin(yaw);
        int roadY = MathHelper.floor(getY() + 0.01) - 1;
        int changed = 0;
        java.util.HashSet<Long> done = new java.util.HashSet<>();
        for (double ahead = 0.5; ahead <= 7.0; ahead += 0.5) {
            for (double l = -ROAD_HALF_WIDTH; l <= ROAD_HALF_WIDTH; l += 0.5) {
                int x = MathHelper.floor(getX() + fx * ahead + lx * l);
                int z = MathHelper.floor(getZ() + fz * ahead + lz * l);
                if (!done.add(BlockPos.asLong(x, 0, z))) continue;
                changed += FreezeEngine.paveColumn(world, x, z, roadY);
            }
        }
        if (changed > 0) {
            IcePoints.award(world.getServer(), driver.getUuid(), (long) changed * POINTS_PER_BLOCK * combo);
        }

        // side cannons: snowballs and spinning ice particles to the left and right
        if (age % 3 == 0) {
            for (int side = -1; side <= 1; side += 2) fireSnowball(world, side, fx, fz, lx, lz);
        }
        for (int side = -1; side <= 1; side += 2) spinParticles(world, side, fx, fz, lx, lz);
        if (age % 7 == 0) {
            world.playSound(null, getBlockPos(), com.iceage.yantra.ModSounds.FREEZE, SoundCategory.NEUTRAL, 0.8f, 0.6f + random.nextFloat() * 0.3f);
        }
        world.spawnParticles(ModParticles.FROST_MIST, getX() - fx * 2.7, getY() + 0.4, getZ() - fz * 2.7, 6, 1.2, 0.3, 1.2, 0.05);
    }

    private void fireSnowball(ServerWorld world, int side, double fx, double fz, double lx, double lz) {
        double ox = getX() + lx * side * 1.7 + fx * (random.nextDouble() * 2.0 - 0.6);
        double oz = getZ() + lz * side * 1.7 + fz * (random.nextDouble() * 2.0 - 0.6);
        SnowballEntity sb = new SnowballEntity(world, ox, getY() + 2.0, oz);
        double out = 1.1 + random.nextDouble() * 0.6;
        sb.setVelocity(lx * side * out + fx * speed * 0.9 + (random.nextDouble() - 0.5) * 0.2, 0.25 + random.nextDouble() * 0.3,
                lz * side * out + fz * speed * 0.9 + (random.nextDouble() - 0.5) * 0.2);
        world.spawnEntity(sb);
    }

    private void spinParticles(ServerWorld world, int side, double fx, double fz, double lx, double lz) {
        double cx = getX() + lx * side * 1.9;
        double cy = getY() + 1.6;
        double cz = getZ() + lz * side * 1.9;
        ItemStackParticleEffect ice = new ItemStackParticleEffect(ParticleTypes.ITEM, new ItemStack(Items.PACKED_ICE));
        for (int k = 0; k < 4; k++) {
            double a = age * 0.9 + k * Math.PI / 2;
            double ca = Math.cos(a), sa = Math.sin(a);
            // ring of radius 0.5 around the cannon axis (the side vector), spinning with the ticks
            double px = cx + fx * ca * 0.5;
            double pz = cz + fz * ca * 0.5;
            double py = cy + sa * 0.5;
            double vx = lx * side * 0.8 + fx * (-sa) * 0.18;
            double vz = lz * side * 0.8 + fz * (-sa) * 0.18;
            double vy = ca * 0.18;
            world.spawnParticles(k % 2 == 0 ? ModParticles.SNOWFLAKE : (k == 1 ? ModParticles.ICE_SHARD : ice), px, py, pz, 0, vx, vy, vz, 1.0);
        }
    }

    /** Gotiya spawns ahead in the van's path (and again, a while later, if he ran off). */
    private void climax(ServerWorld world, LivingEntity driver) {
        if (gotiya != null) {
            Entity g = world.getEntity(gotiya);
            if (g instanceof GotiyaEntity ge && ge.isAlive()) return;
            gotiya = null;
            climaxCooldown = 300;
        }
        if (climaxCooldown > 0) {
            climaxCooldown--;
            return;
        }
        double yaw = Math.toRadians(getYaw());
        Vec3d at = getPos().add(-Math.sin(yaw) * 30, 0, Math.cos(yaw) * 30);
        if (!world.isChunkLoaded(MathHelper.floor(at.x) >> 4, MathHelper.floor(at.z) >> 4)) return;
        GotiyaEntity g = GotiyaEntity.spawnClimax(world, at, driver);
        if (g != null) gotiya = g.getUuid();
    }

    private int comboFor(int ticks) {
        int ramp = Math.max(1, COMBO_RAMP_SECONDS * 20 / MAX_COMBO);
        return Math.min(MAX_COMBO, 1 + ticks / ramp);
    }

    // ------------------------------------------------------------------ client: interpolation + animation
    private void tickLerp() {
        if (isLogicalSideForUpdatingMovement()) {
            lerpSteps = 0;
            updateTrackedPosition(getX(), getY(), getZ());
        }
        if (lerpSteps > 0) {
            double nx = getX() + (lerpX - getX()) / lerpSteps;
            double ny = getY() + (lerpY - getY()) / lerpSteps;
            double nz = getZ() + (lerpZ - getZ()) / lerpSteps;
            double dyaw = MathHelper.wrapDegrees(lerpYaw - getYaw());
            setYaw(getYaw() + (float) dyaw / lerpSteps);
            lerpSteps--;
            setPosition(nx, ny, nz);
            setRotation(getYaw(), getPitch());
        }
    }

    @Override
    public void updateTrackedPositionAndAngles(double x, double y, double z, float yaw, float pitch, int interpolationSteps, boolean interpolate) {
        lerpX = x;
        lerpY = y;
        lerpZ = z;
        lerpYaw = yaw;
        lerpSteps = 6;
    }

    private void clientAnim() {
        prevWheelAngle = wheelAngle;
        prevSteer = steer;
        prevLean = lean;
        double dx = getX() - prevX, dz = getZ() - prevZ;
        float yaw = getYaw() * MathHelper.RADIANS_PER_DEGREE;
        float fwd = (float) (dx * -MathHelper.sin(yaw) + dz * MathHelper.cos(yaw));
        groundSpeed = fwd;
        wheelAngle += fwd / (0.425f * SCALE) * MathHelper.DEGREES_PER_RADIAN;
        float dYaw = MathHelper.wrapDegrees(getYaw() - lastYaw);
        lastYaw = getYaw();
        steer += (MathHelper.clamp(-dYaw * 6f, -28f, 28f) - steer) * 0.3f;
        lean += (MathHelper.clamp(dYaw * Math.abs(fwd) * 9f, -6f, 6f) - lean) * 0.2f;
        if (!getPassengerList().isEmpty() || Math.abs(fwd) > 0.02f) jingleHook.accept(this);
        if (Math.abs(fwd) > 0.08f && random.nextInt(2) == 0) {
            double bx = getX() + MathHelper.sin(yaw) * 2.7, bz = getZ() - MathHelper.cos(yaw) * 2.7;
            getWorld().addParticle(ParticleTypes.CLOUD, bx + (random.nextDouble() - 0.5) * 2, getY() + 0.15, bz + (random.nextDouble() - 0.5) * 2, 0, 0.01, 0);
        }
    }
}
'''),
    'src/main/java/com/iceage/yantra/item/IceVanItem.java': ('text', r'''package com.iceage.yantra.item;

import com.iceage.yantra.ModEntities;
import com.iceage.yantra.entity.IceVanEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Place the Musical Ice Cream Van (a real custom vehicle entity). */
public class IceVanItem extends Item {
    public IceVanItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext ctx) {
        World world = ctx.getWorld();
        if (world.isClient) return ActionResult.SUCCESS;
        BlockPos pos = ctx.getBlockPos().offset(ctx.getSide());
        IceVanEntity van = ModEntities.ICE_VAN.create((ServerWorld) world);
        if (van == null) return ActionResult.FAIL;
        float yaw = ctx.getPlayer() != null ? ctx.getPlayer().getYaw() : 0f;
        van.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, 0f);
        if (!world.isSpaceEmpty(van)) return ActionResult.FAIL;
        if (ctx.getPlayer() != null) van.setOwner(ctx.getPlayer().getUuid());
        world.spawnEntity(van);
        if (ctx.getPlayer() == null || !ctx.getPlayer().getAbilities().creativeMode) ctx.getStack().decrement(1);
        return ActionResult.CONSUME;
    }
}
'''),
    'src/main/resources/fabric.mod.json': ('text', r'''{
  "schemaVersion": 1,
  "id": "ice_age_yantra",
  "version": "${version}",
  "name": "Ice Age Yantra",
  "description": "Freeze the world with comedic Yantras, an Ice Cream Van and one very hot-headed villager.",
  "authors": ["Ice Age Yantra"],
  "license": "MIT",
  "environment": "*",
  "entrypoints": {
    "main": ["com.iceage.yantra.IceAgeYantra"],
    "client": ["com.iceage.yantra.client.IceAgeYantraClient"]
  },
  "depends": {
    "fabricloader": ">=0.14.22",
    "minecraft": "~1.20.1",
    "java": ">=17",
    "fabric-api": "*"
  }
}
'''),
}


ASSET_DIRS = ['textures', 'sounds', 'particles', os.path.join('models', 'obj')]
ASSET_FILES = ['sounds.json', 'CREDITS.txt']
ASSET_REL = os.path.join('src', 'main', 'resources', 'assets', 'ice_age_yantra')


def find_forge_assets(args):
    """Returns (assets folder, project root) of a generated Forge project, running the Forge generator if necessary."""
    candidates = []
    if args.assets_from:
        candidates.append(os.path.abspath(args.assets_from))
    candidates += [os.path.join(HERE, 'ice_age_yantra'), os.path.join(os.getcwd(), 'ice_age_yantra')]
    for c in candidates:
        if os.path.isdir(os.path.join(c, ASSET_REL, 'models', 'obj')):
            return os.path.join(c, ASSET_REL), c
    gen = os.path.abspath(args.forge_generator or os.path.join(HERE, 'build_ice_age_mod.py'))
    if not os.path.isfile(gen):
        sys.exit('No generated Forge project found and %s is missing.\n'
                 'Put build_ice_age_mod.py + source_models next to this script, or use --assets-from DIR.' % gen)
    tmp = tempfile.mkdtemp(prefix='iceage_forge_')
    print('[ice-age] running the Forge asset generator (about 1.5 minutes) ...')
    cmd = [sys.executable, gen, '--out', tmp, '--clean']
    if args.models:
        cmd += ['--models', os.path.abspath(args.models)]
    subprocess.check_call(cmd, cwd=os.path.dirname(gen))
    return os.path.join(tmp, ASSET_REL), tmp


def main():
    ap = argparse.ArgumentParser(description='Generate the Ice Age Yantra Fabric 1.20.1 mod project (port of the Forge mod).')
    ap.add_argument('--out', default='./ice_age_yantra_fabric', help='output folder (default ./ice_age_yantra_fabric)')
    ap.add_argument('--clean', action='store_true', help='delete the output folder first')
    ap.add_argument('--assets-from', help='generated Forge project folder to take models/textures/sounds from')
    ap.add_argument('--forge-generator', help='path of build_ice_age_mod.py (default: next to this script)')
    ap.add_argument('--models', help='folder with the 5 Sketchfab model zips (passed to the Forge generator)')
    args = ap.parse_args()
    out = os.path.abspath(args.out)
    if args.clean and os.path.isdir(out):
        if os.path.exists(os.path.join(out, 'src', 'main', 'resources', 'fabric.mod.json')) or not os.listdir(out):
            shutil.rmtree(out)
        else:
            sys.exit('Refusing to delete %s: it does not look like a generated project.' % out)
    for rel, (kind, data) in FILES.items():
        path = os.path.join(out, rel)
        os.makedirs(os.path.dirname(path), exist_ok=True)
        if kind == 'bin':
            with open(path, 'wb') as f:
                f.write(zlib.decompress(base64.b64decode(data)))
        else:
            with open(path, 'w', encoding='utf-8', newline='\n') as f:
                f.write(data)
    gw = os.path.join(out, 'gradlew')
    os.chmod(gw, os.stat(gw).st_mode | stat.S_IXUSR | stat.S_IXGRP | stat.S_IXOTH)
    print('[ice-age] sources written (%d files)' % len(FILES))

    # the Forge assets: OBJ rigs, textures, sounds, particles
    src_assets, forge_root = find_forge_assets(args)
    dst_assets = os.path.join(out, ASSET_REL)
    for d in ASSET_DIRS:
        if os.path.isdir(os.path.join(dst_assets, d)):
            shutil.rmtree(os.path.join(dst_assets, d))
        shutil.copytree(os.path.join(src_assets, d), os.path.join(dst_assets, d))
    for f in ASSET_FILES:
        shutil.copy2(os.path.join(src_assets, f), os.path.join(dst_assets, f))
    rig = os.path.join(forge_root, 'src', 'main', 'java', 'com', 'iceage', 'yantra', 'RigData.java')
    if os.path.isfile(rig):
        shutil.copy2(rig, os.path.join(out, 'src', 'main', 'java', 'com', 'iceage', 'yantra', 'RigData.java'))
    print('[ice-age] Forge models, textures, sounds and particles copied from', src_assets)

    sys.modules['assetgen'].generate(out, json.loads(ITEM_DISPLAY))
    print('[ice-age] blockstates, item models, lang and loot tables written')
    print('[ice-age] done. Now run:  cd %s && ./gradlew build   (jar in build/libs/ice_age_yantra-1.0.0.jar)' % out)


if __name__ == '__main__':
    main()
