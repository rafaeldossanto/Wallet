#include <flutter/dart_project.h>
#include <flutter/flutter_view_controller.h>
#include <windows.h>

#include "flutter_window.h"
#include "utils.h"

namespace {

constexpr const wchar_t kTitle[] = L"Wallet";
constexpr const wchar_t kWindowClass[] = L"FLUTTER_RUNNER_WIN32_WINDOW";
constexpr const wchar_t kSingleInstanceMutex[] = L"Local\\com.wallet.desktop";

// A second launch (the Start menu, a shortcut) brings the open window forward,
// out of the tray if it is there, instead of opening another Wallet. Returns
// true when one was already open.
bool FocusOpenInstance() {
  ::CreateMutexW(nullptr, TRUE, kSingleInstanceMutex);
  if (::GetLastError() != ERROR_ALREADY_EXISTS) {
    return false;
  }
  HWND open = ::FindWindowW(kWindowClass, kTitle);
  if (open) {
    ::ShowWindow(open, ::IsIconic(open) ? SW_RESTORE : SW_SHOW);
    ::SetForegroundWindow(open);
  }
  return true;
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
  // The Dart side (window_manager) sizes, centres and shows the window, or
  // keeps it in the tray.
  Win32Window::Point origin(10, 10);
  Win32Window::Size size(1280, 800);
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
