// SPDX-License-Identifier: GPL-3.0-only
#include <windows.h>
#include <wincred.h>
#include <jni.h>
#include <string>
#include <vector>

namespace {
void fail(JNIEnv* env, DWORD code) {
    const auto message = std::string("Windows credential storage failed (code ") + std::to_string(code) + ").";
    env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), message.c_str());
}
std::wstring target(JNIEnv* env, jstring key) {
    if (!key) { fail(env, ERROR_INVALID_PARAMETER); return {}; }
    const auto length = env->GetStringLength(key);
    if (length < 1 || length > 100) { fail(env, ERROR_INVALID_PARAMETER); return {}; }
    const auto chars = env->GetStringChars(key, nullptr);
    if (!chars) return {};
    std::wstring result = L"PodiumAirWindows/";
    bool valid = true;
    for (int i = 0; i < length; ++i) {
        const auto c = chars[i];
        if (!((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '-')) { valid = false; break; }
        result.push_back(static_cast<wchar_t>(c));
    }
    env->ReleaseStringChars(key, chars);
    if (!valid) { fail(env, ERROR_INVALID_PARAMETER); return {}; }
    return result;
}
}

extern "C" JNIEXPORT jbyteArray JNICALL Java_com_podium_air_desktop_WindowsCredentials_read(JNIEnv* env, jobject, jstring key) {
    const auto name = target(env, key);
    if (env->ExceptionCheck()) return nullptr;
    PCREDENTIALW credential = nullptr;
    if (!CredReadW(name.c_str(), CRED_TYPE_GENERIC, 0, &credential)) {
        const auto code = GetLastError();
        if (code != ERROR_NOT_FOUND) fail(env, code);
        return nullptr;
    }
    jbyteArray output = nullptr;
    if (credential->CredentialBlobSize <= 2048) {
        output = env->NewByteArray(static_cast<jsize>(credential->CredentialBlobSize));
        if (output) env->SetByteArrayRegion(output, 0, static_cast<jsize>(credential->CredentialBlobSize), reinterpret_cast<jbyte*>(credential->CredentialBlob));
    } else fail(env, ERROR_INVALID_DATA);
    if (credential->CredentialBlob) SecureZeroMemory(credential->CredentialBlob, credential->CredentialBlobSize);
    CredFree(credential);
    return output;
}

extern "C" JNIEXPORT void JNICALL Java_com_podium_air_desktop_WindowsCredentials_write(JNIEnv* env, jobject, jstring key, jbyteArray value) {
    const auto name = target(env, key);
    if (env->ExceptionCheck()) return;
    const auto size = value ? env->GetArrayLength(value) : 0;
    if (size < 1 || size > 2048) { fail(env, ERROR_INVALID_PARAMETER); return; }
    std::vector<BYTE> bytes(static_cast<size_t>(size));
    env->GetByteArrayRegion(value, 0, size, reinterpret_cast<jbyte*>(bytes.data()));
    if (env->ExceptionCheck()) { SecureZeroMemory(bytes.data(), bytes.size()); return; }
    CREDENTIALW credential{};
    credential.Type = CRED_TYPE_GENERIC;
    credential.TargetName = const_cast<wchar_t*>(name.c_str());
    credential.UserName = const_cast<wchar_t*>(L"Podium Air");
    credential.CredentialBlob = bytes.data();
    credential.CredentialBlobSize = static_cast<DWORD>(bytes.size());
    credential.Persist = CRED_PERSIST_LOCAL_MACHINE;
    const auto ok = CredWriteW(&credential, 0);
    const auto code = ok ? ERROR_SUCCESS : GetLastError();
    SecureZeroMemory(bytes.data(), bytes.size());
    if (!ok) fail(env, code);
}

extern "C" JNIEXPORT void JNICALL Java_com_podium_air_desktop_WindowsCredentials_delete(JNIEnv* env, jobject, jstring key) {
    const auto name = target(env, key);
    if (env->ExceptionCheck()) return;
    if (!CredDeleteW(name.c_str(), CRED_TYPE_GENERIC, 0)) {
        const auto code = GetLastError();
        if (code != ERROR_NOT_FOUND) fail(env, code);
    }
}
