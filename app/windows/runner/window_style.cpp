#include "window_style.h"

#include <dwmapi.h>
#include <flutter_windows.h>

namespace window_style {
namespace {

// In logical pixels: the Flutter side draws its border with the same radius.
constexpr int kCornerRadius = 12;

// Attributes newer than some SDKs.
constexpr DWORD kUseImmersiveDarkMode = 20;    // DWMWA_USE_IMMERSIVE_DARK_MODE
constexpr DWORD kWindowCornerPreference = 33;  // DWMWA_WINDOW_CORNER_PREFERENCE, Windows 11
constexpr DWORD kSystemBackdropType = 38;      // DWMWA_SYSTEMBACKDROP_TYPE, Windows 11 22H2
constexpr int kCornerRound = 2;                // DWMWCP_ROUND
constexpr int kBackdropNone = 1;               // DWMSBT_NONE
constexpr int kBackdropAcrylic = 3;            // DWMSBT_TRANSIENTWINDOW

// SetWindowCompositionAttribute is how Windows 10 blurs what is behind a window. user32
// exports it, but the SDK does not declare it.
enum AccentState { kAccentDisabled = 0, kAccentBlurBehind = 3 };

struct AccentPolicy {
  int state;
  int flags;
  DWORD gradient_color;
  int animation_id;
};

struct CompositionAttributeData {
  int attribute;
  PVOID data;
  SIZE_T size;
};

constexpr int kAccentPolicyAttribute = 19;  // WCA_ACCENT_POLICY

void SetAccent(HWND hwnd, AccentState state) {
  using SetWindowCompositionAttributeFn =
      BOOL(WINAPI*)(HWND, CompositionAttributeData*);
  static const auto set_attribute =
      reinterpret_cast<SetWindowCompositionAttributeFn>(::GetProcAddress(
          ::GetModuleHandleW(L"user32.dll"), "SetWindowCompositionAttribute"));
  if (!set_attribute) {
    return;
  }
  AccentPolicy policy = {state, 0, 0, 0};
  CompositionAttributeData data = {kAccentPolicyAttribute, &policy,
                                   sizeof(policy)};
  set_attribute(hwnd, &data);
}

// Asking for round corners only works on Windows 11, so the answer says which system this is.
bool HasNativeCorners(HWND hwnd) {
  static int native = -1;
  if (native < 0) {
    int preference = kCornerRound;
    native = SUCCEEDED(::DwmSetWindowAttribute(
                 hwnd, kWindowCornerPreference, &preference, sizeof(preference)))
                 ? 1
                 : 0;
  }
  return native == 1;
}

}  // namespace

void Apply(HWND hwnd, bool translucent, bool dark) {
  BOOL dark_mode = dark ? TRUE : FALSE;
  ::DwmSetWindowAttribute(hwnd, kUseImmersiveDarkMode, &dark_mode,
                          sizeof(dark_mode));

  // Windows 11 22H2 draws acrylic itself; older systems get the Windows 10 blur.
  int backdrop = translucent ? kBackdropAcrylic : kBackdropNone;
  const bool system_backdrop = SUCCEEDED(::DwmSetWindowAttribute(
      hwnd, kSystemBackdropType, &backdrop, sizeof(backdrop)));
  SetAccent(hwnd, translucent && !system_backdrop ? kAccentBlurBehind
                                                  : kAccentDisabled);

  // The backdrop is drawn on the window frame. Spread over the whole window, it shows through
  // wherever Flutter leaves the view transparent; the text stays drawn on top, sharp.
  MARGINS margins = translucent ? MARGINS{-1, -1, -1, -1} : MARGINS{0, 0, 0, 0};
  ::DwmExtendFrameIntoClientArea(hwnd, &margins);

  UpdateCorners(hwnd);
  ::InvalidateRect(hwnd, nullptr, TRUE);
}

void UpdateCorners(HWND hwnd) {
  if (HasNativeCorners(hwnd)) {
    return;
  }
  if (::IsZoomed(hwnd) || ::IsIconic(hwnd)) {
    ::SetWindowRgn(hwnd, nullptr, TRUE);
    return;
  }
  RECT rect;
  if (!::GetWindowRect(hwnd, &rect)) {
    return;
  }
  const UINT dpi = FlutterDesktopGetDpiForMonitor(
      ::MonitorFromWindow(hwnd, MONITOR_DEFAULTTONEAREST));
  const int diameter = ::MulDiv(kCornerRadius * 2, static_cast<int>(dpi), 96);
  HRGN region =
      ::CreateRoundRectRgn(0, 0, rect.right - rect.left + 1,
                           rect.bottom - rect.top + 1, diameter, diameter);
  // From here on the region belongs to the system, unless it was refused.
  if (::SetWindowRgn(hwnd, region, TRUE) == 0) {
    ::DeleteObject(region);
  }
}

}  // namespace window_style
