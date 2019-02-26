project(suio)

cmake_minimum_required(VERSION 3.4.1)

set(SRC src/main/cpp/suio.c)

add_executable(${PROJECT_NAME} ${SRC})

if (${ANDROID_ABI} MATCHES "arm64-v8a" OR ${ANDROID_ABI} MATCHES "x86_64")
  add_executable(lib${PROJECT_NAME}.so ${SRC})
else()
  add_executable(lib${PROJECT_NAME}.so ${SRC})
  set_target_properties(lib${PROJECT_NAME}.so PROPERTIES LINK_FLAGS -nopie)
  add_executable(lib${PROJECT_NAME}-pie.so ${SRC})
  set_target_properties(lib${PROJECT_NAME}-pie.so PROPERTIES LINK_FLAGS -pie)
endif()
