// Copyright (c) 2024-2026 ScreenerBot (screenerbot.io)
// SPDX-License-Identifier: BUSL-1.1

//! Android JNI entry point. The existing ScreenerBot boot sequence and service
//! manager remain the single source of truth for engine startup.
use jni::{
    objects::{JClass, JString},
    sys::jboolean,
    JNIEnv,
};
use std::sync::{
    atomic::{AtomicBool, Ordering},
    OnceLock,
};
use tokio::runtime::Runtime;

static RUNTIME: OnceLock<Runtime> = OnceLock::new();
static STARTED: AtomicBool = AtomicBool::new(false);

#[no_mangle]
pub extern "system" fn Java_io_screenerbot_mobile_NativeEngine_start(
    mut env: JNIEnv,
    _class: JClass,
    data_directory: JString,
) -> jboolean {
    let directory: String = match env.get_string(&data_directory) {
        Ok(value) => value.into(),
        Err(error) => {
            eprintln!("ScreenerBot Android: invalid data directory: {error}");
            return jni::sys::JNI_FALSE;
        }
    };

    if directory.trim().is_empty() {
        eprintln!("ScreenerBot Android: data directory is empty");
        return jni::sys::JNI_FALSE;
    }

    // Keep every database, config file, and wallet store inside this app's
    // private sandbox. Android's Keystore integration is a separate follow-up;
    // the existing encrypted-wallet mechanism is retained unchanged.
    std::env::set_var("SCREENERBOT_DATA_DIR", directory);

    if STARTED.swap(true, Ordering::SeqCst) {
        return jni::sys::JNI_TRUE;
    }

    let runtime = RUNTIME.get_or_init(|| {
        tokio::runtime::Builder::new_multi_thread()
            .enable_all()
            .thread_name("screenerbot-engine")
            .build()
            .expect("could not create ScreenerBot Tokio runtime")
    });

    runtime.spawn(async {
        crate::run::boot().await;
    });

    jni::sys::JNI_TRUE
}
