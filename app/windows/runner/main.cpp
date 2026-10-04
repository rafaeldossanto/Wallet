#include <flutter/dart_project.h>
#include <flutter/flutter_view_controller.h>
#include <flutter_windows.h>
#include <windows.h>

#include <algorithm>

#include "flutter_window.h"
#include "utils.h"

namespace {

constexpr const wchar_t kTitle[] = L"Wallet";
constexpr const wchar_t kWindowClass[] = L"FLUTTER_RUNNER_WIN32_WINDOW";
constexpr const wchar_t kSingleInstanceMutex[] = L"Local\\com.wallet.desktop";

// A second launch (the Start menu, a shortcut) brings the open window forward
// instead of opening another Wallet. Returns true when one was already open.
bool FocusOpenInstance() {
  ::CreateMutexW(nullptr, TRUE, kSingleInstanceMutex);
  if (::GetLastError() != ERROR_ALREADY_EXISTS) {
    return false;
  }
  HWND open = ::FindWindowW(kWindowClass, kTitle);
  if (open) {
    if (::IsIconic(open)) {
      ::ShowWindow(open, SW_RESTORE);
    }
    ::SetForegroundWindow(open);
  }
  return true;
}

// Centred on the primary monitor's work area (the screen minus the taskbar),
// and never larger than 90% of it. In logical pixels, as Create() expects.
void CenterOnPrimaryMonitor(Win32Window::Point& origin, Win32Window::Size& size) {
  HMONITOR monitor = ::MonitorFromPoint({0, 0}, MONITOR_DEFAULTTOPRIMARY);
  MONITORINFO info{};
  info.cbSize = sizeof(MONITORINFO);
  if (!::GetMonitorInfoW(monitor, &info)) {
    return;
  }
  const double scale = FlutterDesktopGetDpiForMonitor(monitor) / 96.0;
  const RECT& work = info.rcWork;
  const double width = (work.right - work.left) / scale;
  const double height = (work.bottom - work.top) / scale;
  size.width = std::min(size.width, static_cast<unsigned int>(width * 0.9));
  size.height = std::min(size.height, static_cast<unsigned int>(height * 0.9));
  origin.x = static_cast<unsigned int>(work.left / scale + (width - size.width) / 2);
  origin.y = static_cast<unsigned int>(work.top / scale + (height - size.height) / 2);
}

}  // namespace

int APIENTRY wWinMain(_In_ HINSTANCE instance, _In_opt_ HINSTANCE prev,
                      _In_ wchar_t *command_line, _In_ int show_command) {
  if (FocusOpenInstance()) {
    return EXIT_SUCCESS;
  }

  // Attach to console when present (e.g., 'flutter run') or create a
  // new console when running with a debugger.
  if (!::AttachConsole(ATTACH_PARENT_PROCESS) && ::IsDebuggerPresent()) {
    CreateAndAttachConsole();
  }

  // Initialize COM, so that it is available for use in the library and/or
  // plugins.
  ::CoInitializeEx(nullptr, COINIT_APARTMENTTHREADED);

  flutter::DartProject project(L"data");

  std::vector<std::string> command_line_arguments =
      GetCommandLineArguments();

  project.set_dart_entrypoint_arguments(std::move(command_line_arguments));

  FlutterWindow window(project);
  Win32Window::Point origin(10, 10);
  Win32Window::Size size(1280, 800);
  CenterOnPrimaryMonitor(origin, size);
  if (!window.Create(kTitle, origin, size)) {
    return EXIT_FAILURE;
  }
  window.SetQuitOnClose(true);

  ::MSG msg;
  while (::GetMessage(&msg, nullptr, 0, 0)) {
    ::TranslateMessage(&msg);
    ::DispatchMessage(&msg);
  }

  ::CoUninitialize();
  return EXIT_SUCCESS;
}
