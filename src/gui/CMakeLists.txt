target_sources(dosboxcommon PRIVATE
  clipboard.cpp
  mapper.cpp
  sdl_gui.cpp
  titlebar.cpp
  window_geometry.cpp

  render/auto_image_adjustments.cpp
  render/auto_shader_switcher.cpp
  render/deinterlacer.cpp
  render/opengl_renderer.cpp
  render/render.cpp
  render/scaler/scalers.cpp
  render/sdl_renderer.cpp
  render/shader.cpp
  render/shader_manager.cpp
  render/shader_pipeline.cpp
  render/shader_pragma_parser.cpp
)

if(APPLE)
target_sources(dosboxcommon PRIVATE
  macos_window.mm
  render/macos_colorspace.mm
)
endif()

if (OPT_DEBUGGER)
  target_link_libraries(dosboxcommon PRIVATE imgui::imgui)
endif()

target_link_libraries(dosboxcommon PRIVATE simpleini)

# Desktop libGL is only needed when the OpenGL renderer is enabled. Android has
# no desktop GL, so find_package(OpenGL) is skipped there (see top-level CMakeLists).
if (C_OPENGL)
  target_link_libraries(dosboxcommon PRIVATE OpenGL::GL)
endif()

# glad is linked unconditionally: render/shader_pipeline.cpp is not guarded by
# C_OPENGL and uses glad's GL headers/function pointers. It is self-contained
# (no libGL dependency) and unused at runtime when C_OPENGL is off.
target_link_libraries(dosboxcommon PRIVATE glad)
