project(suio)

cmake_minimum_required(VERSION 3.4.1)

set(SRC src/main/cpp/suio.c)

add_executable(${PROJECT_NAME} ${SRC})

if (${ANDROID_ABI} MATCHES "arm64-v8a" OR ${ANDROID_ABI} MATCHES "x86_64")
  set_target_properties(${PROJECT_NAME} PROPERTIES OUTPUT_NAME lib${PROJECT_NAME}.so)
elseif(DEFINED ANDROID_ABI)
  set_target_properties(${PROJECT_NAME} PROPERTIES OUTPUT_NAME lib${PROJECT_NAME}.so)
  set_target_properties(${PROJECT_NAME} PROPERTIES LINK_FLAGS -pie)
  add_executable(${PROJECT_NAME}-nopie ${SRC})
  set_target_properties(${PROJECT_NAME}-nopie PROPERTIES OUTPUT_NAME lib${PROJECT_NAME}-nopie.so)
  set_target_properties(${PROJECT_NAME}-nopie PROPERTIES LINK_FLAGS -nopie)
  target_link_libraries(${PROJECT_NAME}-nopie $<TARGET_PROPERTY:${PROJECT_NAME},LINK_LIBRARIES>)
  add_dependencies(${PROJECT_NAME} ${PROJECT_NAME}-nopie)
endif()
