// SPDX-License-Identifier: GPL-3.0-only
// Windows Common Item Dialogs. Original implementation; no third-party code copied.
#pragma once

namespace podium_shell {
struct WindowSearch { DWORD process; bool unowned; HWND found = nullptr; };
HWND windowFor(DWORD process, bool unowned = true) {
    WindowSearch search{process, unowned};
    EnumWindows([](HWND hwnd, LPARAM data) -> BOOL {
        auto& search = *reinterpret_cast<WindowSearch*>(data);
        DWORD pid = 0; GetWindowThreadProcessId(hwnd, &pid);
        if (pid == search.process && IsWindowVisible(hwnd) && (!search.unowned || !GetWindow(hwnd, GW_OWNER))) { search.found = hwnd; return FALSE; }
        return TRUE;
    }, reinterpret_cast<LPARAM>(&search));
    return search.found;
}
std::wstring pathOf(IShellItem* item) {
    PWSTR path = nullptr; check_hresult(item->GetDisplayName(SIGDN_FILESYSPATH, &path));
    std::wstring result(path); CoTaskMemFree(path); return result;
}
std::vector<std::wstring> choose(std::string const& mode, HWND owner, std::wstring const& name = L"", std::wstring const& folder = L"", int automatic = 0) {
    com_ptr<IFileDialog> dialog;
    com_ptr<IFileOpenDialog> open;
    if (mode == "--save-playlist") {
        com_ptr<IFileSaveDialog> save;
        check_hresult(CoCreateInstance(CLSID_FileSaveDialog, nullptr, CLSCTX_INPROC_SERVER, __uuidof(IFileSaveDialog), save.put_void()));
        dialog = save.as<IFileDialog>();
        check_hresult(dialog->SetDefaultExtension(L"m3u8"));
    } else {
        check_hresult(CoCreateInstance(CLSID_FileOpenDialog, nullptr, CLSCTX_INPROC_SERVER, __uuidof(IFileOpenDialog), open.put_void()));
        dialog = open.as<IFileDialog>();
    }
    FILEOPENDIALOGOPTIONS options{}; check_hresult(dialog->GetOptions(&options));
    options = static_cast<FILEOPENDIALOGOPTIONS>(options | FOS_FORCEFILESYSTEM | FOS_NOCHANGEDIR);
    if (mode == "--pick-folder") options = static_cast<FILEOPENDIALOGOPTIONS>(options | FOS_PICKFOLDERS);
    if (mode == "--pick-files") options = static_cast<FILEOPENDIALOGOPTIONS>(options | FOS_ALLOWMULTISELECT | FOS_FILEMUSTEXIST);
    check_hresult(dialog->SetOptions(options));
    if (mode != "--pick-folder") {
        COMDLG_FILTERSPEC music[] = {{L"Supported music", L"*.mp3;*.wav;*.aif;*.aiff;*.m4a;*.flac;*.opus;*.ogg"}, {L"All files", L"*.*"}};
        COMDLG_FILTERSPEC playlists[] = {{L"Local playlists", L"*.m3u8;*.m3u"}, {L"All files", L"*.*"}};
        check_hresult(dialog->SetFileTypes(2, mode == "--pick-files" ? music : playlists));
    }
    check_hresult(dialog->SetTitle(mode == "--pick-folder" ? L"Import music folder" : mode == "--pick-files" ? L"Import music" : mode == "--save-playlist" ? L"Export local playlist" : L"Import local playlist"));
    if (!name.empty()) check_hresult(dialog->SetFileName(name.c_str()));
    if (!folder.empty()) {
        com_ptr<IShellItem> location;
        check_hresult(SHCreateItemFromParsingName(folder.c_str(), nullptr, __uuidof(IShellItem), location.put_void()));
        check_hresult(dialog->SetFolder(location.get()));
    }
    std::atomic<bool> finished{false};
    std::thread driver;
    if (automatic != 0) driver = std::thread([&finished, automatic] {
        std::this_thread::sleep_for(std::chrono::milliseconds(500));
        for (int i = 0; i < 100 && !finished; ++i) {
            auto hwnd = windowFor(GetCurrentProcessId(), false);
            if (hwnd) PostMessageW(hwnd, WM_COMMAND, i < 70 ? automatic : IDCANCEL, 0);
            std::this_thread::sleep_for(std::chrono::milliseconds(100));
        }
    });
    HRESULT shown = dialog->Show(owner);
    finished = true; if (driver.joinable()) driver.join();
    if (shown == HRESULT_FROM_WIN32(ERROR_CANCELLED)) return {};
    check_hresult(shown);
    std::vector<std::wstring> paths;
    if (mode == "--pick-files") {
        com_ptr<IShellItemArray> results; check_hresult(open->GetResults(results.put()));
        DWORD count = 0; check_hresult(results->GetCount(&count));
        if (count > 10000) throw std::runtime_error("Select at most 10000 files at a time");
        for (DWORD i = 0; i < count; ++i) { com_ptr<IShellItem> item; check_hresult(results->GetItemAt(i, item.put())); paths.push_back(pathOf(item.get())); }
    } else { com_ptr<IShellItem> item; check_hresult(dialog->GetResult(item.put())); paths.push_back(pathOf(item.get())); }
    return paths;
}
void selfTest() {
    wchar_t temp[MAX_PATH], seed[MAX_PATH];
    if (!GetTempPathW(MAX_PATH, temp) || !GetTempFileNameW(temp, L"pod", 0, seed)) throw std::runtime_error("Could not make shell dialog fixture");
    DeleteFileW(seed); std::wstring folder = std::wstring(seed) + L"-\u97f3\u4e50";
    if (!CreateDirectoryW(folder.c_str(), nullptr)) throw std::runtime_error("Could not make shell dialog directory");
    std::wstring file = folder + L"\\\u00e9\u97f3.wav";
    HANDLE fixture = CreateFileW(file.c_str(), GENERIC_WRITE, 0, nullptr, CREATE_ALWAYS, FILE_ATTRIBUTE_NORMAL, nullptr);
    if (fixture == INVALID_HANDLE_VALUE) throw std::runtime_error("Could not make unicode shell dialog file");
    CloseHandle(fixture);
    try {
        auto files = choose("--pick-files", nullptr, L"\u00e9\u97f3.wav", folder, IDOK);
        if (files.size() != 1 || files[0] != file) throw std::runtime_error("Native file dialog selected the wrong path");
        auto directories = choose("--pick-folder", nullptr, L"", folder, IDOK);
        if (directories.size() != 1 || directories[0] != folder) throw std::runtime_error("Native folder dialog selected the wrong directory");
        auto saved = choose("--save-playlist", nullptr, L"\u00e9\u97f3.m3u8", folder, IDOK);
        if (saved.size() != 1 || saved[0] != folder + L"\\\u00e9\u97f3.m3u8") throw std::runtime_error("Native save dialog selected the wrong path");
        if (!choose("--pick-files", nullptr, L"", folder, IDCANCEL).empty()) throw std::runtime_error("Native dialog cancellation returned a path");
        emit("PASS: Windows shell dialogs selected a Unicode music file and folder, selected an M3U8 save path, and cancelled without importing.");
    } catch (...) { DeleteFileW(file.c_str()); RemoveDirectoryW(folder.c_str()); throw; }
    DeleteFileW(file.c_str()); RemoveDirectoryW(folder.c_str());
}
int run(int argc, char** argv) {
    init_apartment(apartment_type::single_threaded);
    std::string mode(argv[1]);
    if (mode == "--picker-self-test") { selfTest(); return 0; }
    DWORD ownerPid = 0; std::wstring name;
    for (int i = 2; i < argc; ++i) {
        std::string argument(argv[i]);
        if (argument.starts_with("--owner-pid=")) ownerPid = static_cast<DWORD>(std::stoul(argument.substr(12)));
        if (argument.starts_with("--name=")) name = unhex(argument.substr(7));
    }
    auto paths = choose(mode, ownerPid ? windowFor(ownerPid) : nullptr, name);
    if (paths.empty()) { emit("CANCEL"); return 0; }
    size_t total = 0;
    for (auto const& path : paths) {
        auto bytes = to_string(hstring(path)); total += bytes.size() * 2;
        if (total > 8 * 1024 * 1024) throw std::runtime_error("Selected paths exceed the supported size limit");
        std::string hex; const char* digits = "0123456789abcdef";
        for (unsigned char byte : bytes) { hex += digits[byte >> 4]; hex += digits[byte & 15]; }
        emit("PATH\t" + hex);
    }
    emit("DONE"); return 0;
}
}
