#ifndef RUNNER_WINDOW_STYLE_H_
#define RUNNER_WINDOW_STYLE_H_

#include <windows.h>

// The Wallet window's look, picked on the Dart side (lib/core/desktop/window_frame.dart):
// rounded corners like a macOS window, and a background that is either solid or see-through
// with what is behind it blurred.
namespace window_style {

// Turns the blurred background on or off and matches the system parts to the theme.
void Apply(HWND hwnd, bool translucent, bool dark);

// Rounds the corners for the window's current size: Windows 11 does it by itself, Windows 10
// gets a rounded window region. Square while maximized.
void UpdateCorners(HWND hwnd);

}  // namespace window_style

#endif  // RUNNER_WINDOW_STYLE_H_
