cmake_minimum_required(VERSION 3.4.1)

add_executable(libsuio.so src/main/cpp/suio.c)

add_executable(libsuio-pie.so src/main/cpp/suio.c)

set_target_properties(libsuio-pie.so PROPERTIES LINK_FLAGS -pie)
