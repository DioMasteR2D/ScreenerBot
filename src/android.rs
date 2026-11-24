// Copyright (c) 2024-2026 ScreenerBot (screenerbot.io)
// SPDX-License-Identifier: BUSL-1.1

//! Android JNI entry point. The Rust runtime and all ScreenerBot subsystems run
//! inside the app process; persistent files live in the app-private data folder.

use jni::objects::{JClass, JString};
use jni::sys::{jboolean, JNI_FALSE, JNI_TRUE};
use jni::JNIEnv;
use std::sync::atomic::{AtomicBool, Ordering};

static RUNTIME_STARTED: AtomicBool = AtomicBool::new(false);

#[no_mangle]
pub extern "system" fn Java_com_diomaster_solanahunter_NativeCore_start(
    mut env: JNIEnv,
    _class: JClass,
    data_directory: JString,
) -> jboolean {
    let directory: String = match env.get_string(&data_directory) {
        Ok(value) => value.into(),
        Err(_) => return JNI_FALSE,
    };
    if directory.trim().is_empty() {
        return JNI_FALSE;
    }
    if RUNTIME_STARTED
        .compare_exchange(false, true, Ordering::AcqRel, Ordering::Acquire)
        .is_err()
    {
        return JNI_TRUE;
    }

    let spawn = std::thread::Builder::new()
        .name("screenerbot-runtime".to_owned())
        .spawn(move || {
            // Android's private app directory is not shared with other apps.
            std::env::set_var("SCREENERBOT_DATA_DIR", directory);
            crate::arguments::set_cmd_args(vec![
                "screenerbot".to_owned(),
                "--host".to_owned(),
                "127.0.0.1".to_owned(),
                "--port".to_owned(),
                "8080".to_owned(),
            ]);

            match tokio::runtime::Builder::new_multi_thread()
                .enable_all()
                .build()
            {
                Ok(runtime) => runtime.block_on(crate::run::boot()),
                Err(error) => {
                    eprintln!("ScreenerBot Android runtime failed: {error}");
                    RUNTIME_STARTED.store(false, Ordering::Release);
                }
            }
        });

    if spawn.is_err() {
        RUNTIME_STARTED.store(false, Ordering::Release);
        JNI_FALSE
    } else {
        JNI_TRUE
    }
}
