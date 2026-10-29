// SPDX-FileCopyrightText:  2026 The DOSBox Staging Team
// SPDX-License-Identifier: GPL-2.0-or-later

// Android-only JNI glue. Called from MainActivity (Java) right after the native
// libraries are loaded and BEFORE SDL starts the DOSBox main thread, so that:
//
//  - HOME / XDG_CONFIG_HOME / XDG_DATA_HOME point into the app's private
//    storage (DOSBox resolves its config directory through these on POSIX), and
//  - the working directory is the folder containing the extracted `resources/`
//    directory (get_resource_path() checks "./resources" first).

#include <jni.h>

#include <cstdlib>
#include <string>
#include <unistd.h>

extern "C" JNIEXPORT jint JNICALL
Java_io_dosboxstaging_android_MainActivity_nativeSetup(JNIEnv* env, jclass,
                                                       jstring jfiles_dir)
{
	const char* c_dir = env->GetStringUTFChars(jfiles_dir, nullptr);
	if (!c_dir) {
		return -1;
	}
	const std::string files_dir = c_dir;
	env->ReleaseStringUTFChars(jfiles_dir, c_dir);

	const auto config_home = files_dir + "/config";
	const auto data_home   = files_dir + "/data";

	setenv("HOME", files_dir.c_str(), 1);
	setenv("XDG_CONFIG_HOME", config_home.c_str(), 1);
	setenv("XDG_DATA_HOME", data_home.c_str(), 1);

	return chdir(files_dir.c_str());
}
