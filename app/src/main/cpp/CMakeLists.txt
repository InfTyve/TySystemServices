cmake_minimum_required(VERSION 3.22.1)
project(termuxpty C)

add_library(termux SHARED pty.c)

find_library(log-lib log)
target_link_libraries(termux ${log-lib})
