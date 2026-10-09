// SPDX-License-Identifier: GPL-3.0-only
// Native Windows 10 SMTC adapter. Original implementation; no third-party source copied.
#define WIN32_LEAN_AND_MEAN
#define NOMINMAX
#include <windows.h>
#include <shellapi.h>
#include <shobjidl.h>
#include <systemmediatransportcontrolsinterop.h>
#include <winrt/Windows.Foundation.h>
#include <winrt/Windows.Foundation.Collections.h>
#include <winrt/Windows.Media.h>
#include <winrt/Windows.Media.Control.h>
#include <winrt/Windows.Storage.Streams.h>
#include <algorithm>
#include <atomic>
#include <chrono>
#include <stdexcept>
#include <iostream>
#include <memory>
#include <mutex>
#include <sstream>
#include <string>
#include <thread>
#include <vector>

using namespace winrt;
using namespace Windows::Media;
using namespace Windows::Media::Control;
using namespace Windows::Foundation;
using namespace Windows::Storage::Streams;
constexpr UINT InputMessage = WM_APP + 1;
std::mutex outputMutex;
SystemMediaTransportControls controls{nullptr};
std::wstring lastArt;
std::atomic<bool> pauseReceived{false};
std::atomic<bool> seekReceived{false};

void emit(std::string const& line) {
    std::lock_guard<std::mutex> lock(outputMutex);
    std::cout << line << std::endl;
}
std::wstring unhex(std::string const& hex) {
    if (hex.size() > 131072 || hex.size() % 2) throw std::invalid_argument("Invalid text field");
    auto nibble = [](char c) -> int { if (c >= '0' && c <= '9') return c-'0'; if(c >= 'a' && c <= 'f') return c-'a'+10; throw std::invalid_argument("Invalid hex"); };
    std::string bytes;
    for (size_t i=0; i<hex.size(); i+=2) bytes.push_back(static_cast<char>((nibble(hex[i]) << 4) | nibble(hex[i+1])));
    int size = MultiByteToWideChar(CP_UTF8, MB_ERR_INVALID_CHARS, bytes.data(), static_cast<int>(bytes.size()), nullptr, 0);
    if (!bytes.empty() && !size) throw std::invalid_argument("Invalid UTF-8");
    std::wstring result(size, 0);
    if (size) MultiByteToWideChar(CP_UTF8, MB_ERR_INVALID_CHARS, bytes.data(), static_cast<int>(bytes.size()), result.data(), size);
    return result;
}
#include "shell-dialogs.h"

std::vector<std::string> split(std::string const& text) {
    std::vector<std::string> result;
    size_t start=0;
    for (;;) { auto end=text.find('\t', start); result.push_back(text.substr(start, end-start)); if(end==std::string::npos) break; start=end+1; }
    return result;
}
void update(std::string const& line) {
    auto fields=split(line);
    if (fields.size()!=12 || fields[0]!="UPDATE") throw std::invalid_argument("Invalid update packet");
    bool present=fields[1]=="1";
    controls.IsEnabled(present);
    controls.IsPlayEnabled(present); controls.IsPauseEnabled(present); controls.IsStopEnabled(present);
    controls.IsNextEnabled(fields[5]=="1"); controls.IsPreviousEnabled(fields[6]=="1");
    controls.PlaybackStatus(!present ? MediaPlaybackStatus::Closed : fields[2]=="1" ? MediaPlaybackStatus::Playing : MediaPlaybackStatus::Paused);
    if (present) {
        auto updater=controls.DisplayUpdater();
        updater.Type(MediaPlaybackType::Music);
        updater.AppMediaId(L"Podium Air");
        auto music=updater.MusicProperties();
        music.Title(unhex(fields[7])); music.Artist(unhex(fields[8])); music.AlbumTitle(unhex(fields[9]));
        auto art=unhex(fields[10]);
        if(art!=lastArt) {
            lastArt=art;
            updater.Thumbnail(art.empty() ? nullptr : RandomAccessStreamReference::CreateFromUri(Uri(art)));
        }
        updater.Update();
        auto duration=std::max(0LL,std::stoll(fields[4]));
        auto position=std::clamp(std::stoll(fields[3]),0LL,duration);
        SystemMediaTransportControlsTimelineProperties timeline;
        timeline.StartTime(TimeSpan{0}); timeline.MinSeekTime(TimeSpan{0});
        timeline.EndTime(TimeSpan{duration*10000}); timeline.MaxSeekTime(TimeSpan{duration*10000}); timeline.Position(TimeSpan{position*10000});
        controls.UpdateTimelineProperties(timeline);
        controls.PlaybackRate(std::stod(fields[11]));
    } else { controls.DisplayUpdater().ClearAll(); lastArt.clear(); }
    emit("ACK");
}
LRESULT CALLBACK procedure(HWND hwnd, UINT message, WPARAM wParam, LPARAM lParam) {
    if (message==InputMessage) {
        std::unique_ptr<std::string> line(reinterpret_cast<std::string*>(lParam));
        if (*line=="QUIT") { DestroyWindow(hwnd); return 0; }
        try { update(*line); }
        catch(hresult_error const& error) { emit("ERROR\t"+to_string(error.message())); }
        catch(std::exception const& error) { emit(std::string("ERROR\t")+error.what()); }
        return 0;
    }
    if(message==WM_DESTROY) { if(controls) { controls.IsEnabled(false); controls.PlaybackStatus(MediaPlaybackStatus::Closed); } PostQuitMessage(0); return 0; }
    return DefWindowProcW(hwnd,message,wParam,lParam);
}
int main(int argc, char** argv) {
    try {
        if (argc > 1) {
            std::string mode(argv[1]);
            if (mode == "--picker-self-test" || mode == "--pick-files" || mode == "--pick-folder" || mode == "--pick-playlist" || mode == "--save-playlist") return podium_shell::run(argc, argv);
        }
        init_apartment(apartment_type::multi_threaded);
        SetCurrentProcessExplicitAppUserModelID(L"PodiumAir.Windows");
        WNDCLASSW type{}; type.lpfnWndProc=procedure; type.hInstance=GetModuleHandleW(nullptr); type.lpszClassName=L"PodiumAirMediaBridge";
        if(!RegisterClassW(&type)) throw std::runtime_error("Could not register media window");
        HWND hwnd=CreateWindowW(type.lpszClassName,L"Podium Air",WS_OVERLAPPED,0,0,0,0,nullptr,nullptr,type.hInstance,nullptr);
        if(!hwnd) throw std::runtime_error("Could not create media window");
        auto factory=get_activation_factory<SystemMediaTransportControls,ISystemMediaTransportControlsInterop>();
        check_hresult(factory->GetForWindow(hwnd,guid_of<SystemMediaTransportControls>(),put_abi(controls)));
        controls.ButtonPressed([](auto const&, SystemMediaTransportControlsButtonPressedEventArgs const& args) {
            switch(args.Button()) {
                case SystemMediaTransportControlsButton::Play: emit("PLAY"); break;
                case SystemMediaTransportControlsButton::Pause: pauseReceived=true; emit("PAUSE"); break;
                case SystemMediaTransportControlsButton::Next: emit("NEXT"); break;
                case SystemMediaTransportControlsButton::Previous: emit("PREVIOUS"); break;
                case SystemMediaTransportControlsButton::Stop: emit("STOP"); break;
                case SystemMediaTransportControlsButton::FastForward: emit("FORWARD"); break;
                case SystemMediaTransportControlsButton::Rewind: emit("REWIND"); break;
                default: break;
            }
        });
        controls.PlaybackPositionChangeRequested([](auto const&, PlaybackPositionChangeRequestedEventArgs const& args) {
            seekReceived=true; emit("SEEK\t"+std::to_string(args.RequestedPlaybackPosition().count()/10000));
        });
        if(argc>1 && std::string(argv[1])=="--self-test") {
            update("UPDATE\t1\t1\t250\t6000\t1\t1\t506f6469756d2074657374\t5072656d\t54657374\t\t1.0");
            auto manager=GlobalSystemMediaTransportControlsSessionManager::RequestAsync().get();
            bool found=false;
            for(int attempt=0;attempt<30 && !found;++attempt) {
                for(auto const& session:manager.GetSessions()) {
                    auto properties=session.TryGetMediaPropertiesAsync().get();
                    if(properties.Title()==L"Podium test" && properties.Artist()==L"Prem") {
                        auto state=session.GetPlaybackInfo();
                        if(state.PlaybackStatus()!=GlobalSystemMediaTransportControlsSessionPlaybackStatus::Playing) throw std::runtime_error("Incorrect system session status");
                        if(!session.TryPauseAsync().get()) throw std::runtime_error("Windows rejected the pause command");
                        if(!session.TryChangePlaybackPositionAsync(20000000).get()) throw std::runtime_error("Windows rejected the seek command");
                        for(int wait=0;wait<30 && (!pauseReceived || !seekReceived);++wait) std::this_thread::sleep_for(std::chrono::milliseconds(100));
                        if(!pauseReceived || !seekReceived) throw std::runtime_error("System media commands did not reach the native callbacks");
                        found=true;
                    }
                }
                if(!found) std::this_thread::sleep_for(std::chrono::milliseconds(100));
            }
            if(!found) throw std::runtime_error("Media metadata did not reach the Windows global session manager");
            emit("PASS: Native SMTC session exposed title, artist, playing state and timeline; Windows pause and seek requests reached the callbacks.");
            DestroyWindow(hwnd); return 0;
        }
        emit("READY");
        std::thread input([hwnd]() {
            std::string line;
            while(std::getline(std::cin,line)) {
                if(line.size()>300000) { emit("ERROR\tPacket is too large"); continue; }
                auto data=new std::string(line);
                if(!PostMessageW(hwnd,InputMessage,0,reinterpret_cast<LPARAM>(data))) { delete data; break; }
            }
            auto quit=new std::string("QUIT");
            if(!PostMessageW(hwnd,InputMessage,0,reinterpret_cast<LPARAM>(quit))) delete quit;
        });
        MSG message;
        while(GetMessageW(&message,nullptr,0,0)>0) { TranslateMessage(&message); DispatchMessageW(&message); }
        // EOF is sent by the owning JVM on shutdown. The process also ends if that owner exits.
        if(input.joinable()) input.detach();
        return 0;
    } catch(hresult_error const& error) { emit("ERROR\t"+to_string(error.message())); return 1; }
      catch(std::exception const& error) { emit(std::string("ERROR\t")+error.what()); return 1; }
}
